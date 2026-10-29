import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Download, FileUp } from 'lucide-react'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { ApiErrorAlert } from '@/components/api-error-alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useWorkspace } from '@/features/workspace/workspace-context'
import { apiFetch } from '@/lib/api'

interface ImportRow {
  line: number
  name: string | null
  email: string | null
  phone: string | null
  status: 'VALID' | 'INVALID' | 'DUPLICATE'
  errors: string[]
  duplicateOf: string | null
}

interface ImportResult {
  total: number
  valid: number
  invalid: number
  duplicates: number
  imported: number
  rows: ImportRow[]
}

const upload = (ws: string, file: File, dryRun: boolean) => {
  const form = new FormData()
  form.append('file', file)
  return apiFetch<ImportResult>(`/workspaces/${ws}/clients/import?dryRun=${dryRun}`, { method: 'POST', body: form })
}

/** Template with a BOM so Excel opens the Cyrillic headers correctly. */
function downloadTemplate() {
  const csv = '﻿name;email;phone;company;address;notes\r\nАсель Нурланова;asel@example.kz;+7 701 000 00 00;ТОО «Алма»;Алматы;\r\n'
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = 'clientflow-clients-template.csv'
  a.click()
  URL.revokeObjectURL(url)
}

const STATUS_VARIANT = { VALID: 'secondary', INVALID: 'destructive', DUPLICATE: 'outline' } as const

/** Two steps: preview (nothing is saved) -> import of the valid new rows. */
export function ImportClientsDialog() {
  const { t } = useTranslation()
  const { workspaceId } = useWorkspace()
  const queryClient = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [open, setOpen] = useState(false)
  const [file, setFile] = useState<File | null>(null)

  const preview = useMutation({ mutationFn: (f: File) => upload(workspaceId, f, true) })
  const commit = useMutation({
    mutationFn: (f: File) => upload(workspaceId, f, false),
    onSuccess: (result) => {
      toast.success(t('import.done', { count: result.imported }))
      void queryClient.invalidateQueries({ queryKey: ['ws', workspaceId, 'clients'] })
      setOpen(false)
    },
  })

  const reset = () => {
    setFile(null)
    preview.reset()
    commit.reset()
  }
  const describe = (row: ImportRow) => {
    if (row.status === 'INVALID') {
      return t('import.invalidFields', { fields: row.errors.map((f) => t(`clients.${f}`, { defaultValue: f })).join(', ') })
    }
    if (row.status === 'DUPLICATE') {
      return row.duplicateOf === '#file' ? t('import.duplicateOfFile') : t('import.duplicateOf', { name: row.duplicateOf })
    }
    return ''
  }
  const result = preview.data

  return (
    <Dialog open={open} onOpenChange={(next) => { setOpen(next); if (!next) reset() }}>
      <DialogTrigger asChild>
        <Button variant="outline"><FileUp />{t('import.open')}</Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t('import.title')}</DialogTitle>
          <DialogDescription>{t('import.hint')}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" onClick={downloadTemplate}><Download />{t('import.template')}</Button>
          <Button size="sm" onClick={() => input.current?.click()} disabled={preview.isPending}>
            <FileUp />{t('import.choose')}
          </Button>
          <input
            ref={input}
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            aria-label={t('import.choose')}
            onChange={(e) => {
              const chosen = e.target.files?.[0]
              e.target.value = ''
              if (chosen) {
                setFile(chosen)
                commit.reset()
                preview.mutate(chosen)
              }
            }}
          />
          {file ? <span className="self-center text-sm text-muted-foreground">{file.name}</span> : null}
        </div>
        <ApiErrorAlert error={preview.error ?? commit.error} />

        {result ? (
          <>
            <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
              {(['total', 'valid', 'invalid', 'duplicates'] as const).map((k) => (
                <div key={k} className="rounded-md border p-2">
                  <dt className="text-muted-foreground">{t(`import.${k}`)}</dt>
                  <dd className="text-xl font-semibold">{result[k]}</dd>
                </div>
              ))}
            </dl>
            <div className="max-h-72 overflow-y-auto rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-16">{t('import.line')}</TableHead>
                    <TableHead>{t('clients.name')}</TableHead>
                    <TableHead>{t('import.status')}</TableHead>
                    <TableHead className="hidden sm:table-cell">{t('import.details')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.rows.map((row) => (
                    <TableRow key={row.line}>
                      <TableCell className="tabular-nums">{row.line}</TableCell>
                      <TableCell className="whitespace-normal">
                        {row.name ?? '—'}
                        <span className="block text-xs text-muted-foreground">{[row.email, row.phone].filter(Boolean).join(', ')}</span>
                      </TableCell>
                      <TableCell><Badge variant={STATUS_VARIANT[row.status]}>{t(`import.${row.status}`)}</Badge></TableCell>
                      <TableCell className="hidden whitespace-normal text-xs sm:table-cell">{describe(row)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            {result.valid === 0 ? <p className="text-sm text-muted-foreground">{t('import.nothingToImport')}</p> : null}
          </>
        ) : null}

        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>{t('common.cancel')}</Button>
          <Button disabled={!file || !result || result.valid === 0 || commit.isPending}
                  onClick={() => file && commit.mutate(file)}>
            {t('import.confirm', { count: result?.valid ?? 0 })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
