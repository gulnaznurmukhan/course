import { describe, expect, it } from 'vitest'

import { resources, SUPPORTED_LANGUAGES } from './index'

type Tree = { [key: string]: string | Tree }

function flatten(tree: Tree, prefix = ''): Record<string, string> {
  return Object.entries(tree).reduce<Record<string, string>>((acc, [key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string') acc[path] = value
    else Object.assign(acc, flatten(value, path))
    return acc
  }, {})
}

const PLURAL_SUFFIX = /_(zero|one|two|few|many|other)$/

/** Keys without plural suffixes: "x_one", "x_few" -> "x". */
function baseKeys(keys: string[]) {
  return [...new Set(keys.map((k) => k.replace(PLURAL_SUFFIX, '')))].sort()
}

describe('translations', () => {
  const english = Object.keys(flatten(resources.en.translation as Tree))

  it.each(SUPPORTED_LANGUAGES)('%s has the same keys as English', (lng) => {
    const keys = Object.keys(flatten(resources[lng].translation as Tree))
    expect(baseKeys(keys)).toEqual(baseKeys(english))
  })

  it.each(SUPPORTED_LANGUAGES)('%s has every plural form its grammar needs', (lng) => {
    const keys = Object.keys(flatten(resources[lng].translation as Tree))
    const pluralBases = baseKeys(keys.filter((k) => PLURAL_SUFFIX.test(k)))
    const categories = new Intl.PluralRules(lng).resolvedOptions().pluralCategories
    for (const base of pluralBases) {
      for (const category of categories) {
        expect(keys, `${lng}: ${base}_${category}`).toContain(`${base}_${category}`)
      }
    }
  })

  it.each(SUPPORTED_LANGUAGES)('%s has no empty values', (lng) => {
    const values = Object.values(flatten(resources[lng].translation as Tree))
    expect(values.every((v) => v.trim().length > 0)).toBe(true)
  })
})
