-- ClientFlow: demo data for Supabase (schema "clientflow").
--
-- Run in the Supabase SQL editor AFTER the backend has started at least once
-- (Flyway creates the tables). Safe to run twice: every row has a fixed id and
-- is inserted with ON CONFLICT DO NOTHING.
--
-- Accounts (email already verified, password for all three):  ClientFlow2026!
--   aigerim@demo.clientflow.kz   OWNER     of workspace "Студия Aigerim"
--   dana@demo.clientflow.kz      MANAGER
--   arman@demo.clientflow.kz     EMPLOYEE  (sees only the orders assigned to him)
--
-- The password hash is Argon2id with Spring Security's default parameters
-- (m=16384, t=2, p=1), so the backend accepts it as is.

BEGIN;
SET search_path TO clientflow;

-- ---------------------------------------------------------------- users
INSERT INTO users (id, full_name, email, password_hash, email_verified, preferred_language, theme)
VALUES
  ('11111111-1111-4111-8111-111111111111', 'Айгерим Бекова',  'aigerim@demo.clientflow.kz',
   '$argon2id$v=19$m=16384,t=2,p=1$Jx9JbrhYpH/m4IHuj25HnQ$HotA/LWYMwaauMpir5QTViINhS1e8lZlWCLAdnLHQ4E', TRUE, 'ru', 'SYSTEM'),
  ('11111111-1111-4111-8111-111111111112', 'Дана Сейткали',   'dana@demo.clientflow.kz',
   '$argon2id$v=19$m=16384,t=2,p=1$Jx9JbrhYpH/m4IHuj25HnQ$HotA/LWYMwaauMpir5QTViINhS1e8lZlWCLAdnLHQ4E', TRUE, 'kk', 'LIGHT'),
  ('11111111-1111-4111-8111-111111111113', 'Арман Жумабеков', 'arman@demo.clientflow.kz',
   '$argon2id$v=19$m=16384,t=2,p=1$Jx9JbrhYpH/m4IHuj25HnQ$HotA/LWYMwaauMpir5QTViINhS1e8lZlWCLAdnLHQ4E', TRUE, 'ru', 'DARK')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------- workspace + requisites
INSERT INTO workspaces (id, name, next_order_number, legal_name, bin, address, phone, email, bank_name, iban, bik)
VALUES ('22222222-2222-4222-8222-222222222222', 'Студия Aigerim', 9,
        'ИП «Бекова А.С.»', '900101400123', 'г. Алматы, ул. Абая 10, офис 4', '+7 727 300 10 10',
        'studio@demo.clientflow.kz', 'АО «Kaspi Bank»', 'KZ86722S000012345678', 'CASPKZKA')
ON CONFLICT (id) DO NOTHING;

INSERT INTO workspace_memberships (id, workspace_id, user_id, role) VALUES
  ('33333333-3333-4333-8333-333333333331', '22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111', 'OWNER'),
  ('33333333-3333-4333-8333-333333333332', '22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111112', 'MANAGER'),
  ('33333333-3333-4333-8333-333333333333', '22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111113', 'EMPLOYEE')
ON CONFLICT (workspace_id, user_id) DO NOTHING;

-- ---------------------------------------------------------------- price list
INSERT INTO services (id, workspace_id, name, description, unit, price, currency) VALUES
  ('44444444-4444-4444-8444-444444444441', '22222222-2222-4222-8222-222222222222', 'Дизайн логотипа',        '3 варианта, 2 раунда правок', 'шт',  120000.00, 'KZT'),
  ('44444444-4444-4444-8444-444444444442', '22222222-2222-4222-8222-222222222222', 'Лендинг (1 страница)',   'Дизайн и вёрстка',            'шт',  250000.00, 'KZT'),
  ('44444444-4444-4444-8444-444444444443', '22222222-2222-4222-8222-222222222222', 'Фирменный стиль',        'Логотип, цвета, шрифты, гайд', 'шт', 380000.00, 'KZT'),
  ('44444444-4444-4444-8444-444444444444', '22222222-2222-4222-8222-222222222222', 'Ведение Instagram',      'Контент-план и 12 постов',     'мес', 90000.00, 'KZT'),
  ('44444444-4444-4444-8444-444444444445', '22222222-2222-4222-8222-222222222222', 'Час консультации',       NULL,                           'ч',   15000.00, 'KZT'),
  ('44444444-4444-4444-8444-444444444446', '22222222-2222-4222-8222-222222222222', 'UI kit (Figma)',         'Компоненты и типографика',     'шт',  600.00,    'USD')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------- clients
INSERT INTO clients (id, workspace_id, name, email, phone, company, address, notes, archived_at) VALUES
  ('55555555-5555-4555-8555-555555555551', '22222222-2222-4222-8222-222222222222', 'Асель Нурланова',   'asel@coffeepoint.kz',  '+7 701 555 10 33', 'Coffee Point',        'Алматы, Достык 85',     'Любит быстрые ответы в WhatsApp. Предпочитает оплату переводом.', NULL),
  ('55555555-5555-4555-8555-555555555552', '22222222-2222-4222-8222-222222222222', 'Ерлан Касымов',     'erlan@nomadfit.kz',    '+7 702 123 45 67', 'Nomad Fit',           'Астана, Мәңгілік Ел 30', NULL, NULL),
  ('55555555-5555-4555-8555-555555555553', '22222222-2222-4222-8222-222222222222', 'Мадина Темирова',   'madina@flowers.kz',    '+7 705 987 65 43', 'Flowers & Co',        NULL, 'Сезонные заказы к 8 марта и 1 сентября.', NULL),
  ('55555555-5555-4555-8555-555555555554', '22222222-2222-4222-8222-222222222222', 'Olzhas Bekturov',   'olzhas@qazdev.io',     '+7 777 222 33 44', 'QazDev LLP',          'Almaty, Al-Farabi 77', 'Платит в USD.', NULL),
  ('55555555-5555-4555-8555-555555555555', '22222222-2222-4222-8222-222222222222', 'Жанар Оспанова',    NULL,                   '+7 747 000 11 22', NULL,                  NULL, 'Частный заказчик.', NULL),
  ('55555555-5555-4555-8555-555555555556', '22222222-2222-4222-8222-222222222222', 'Тимур Абдрахманов', 'timur@oldclient.kz',   '+7 700 111 22 33', 'Закрытое кафе',       NULL, 'Бизнес закрыт в 2025.', now() - interval '40 days')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------- orders
-- Statuses: NEW -> CONFIRMED -> IN_PROGRESS -> COMPLETED, CANCELLED. Archive only finished ones.
INSERT INTO customer_orders (id, workspace_id, client_id, order_number, title, description, total_amount, currency,
                             status, deadline, assigned_user_id, created_by, archived_at, created_at, updated_at) VALUES
  -- 1: completed and fully paid, archived
  ('66666666-6666-4666-8666-666666666661', '22222222-2222-4222-8222-222222222222', '55555555-5555-4555-8555-555555555551',
   'ORD-000001', 'Логотип Coffee Point', 'Три варианта, выбран второй.', 120000.00, 'KZT', 'COMPLETED',
   CURRENT_DATE - 60, '11111111-1111-4111-8111-111111111113', '11111111-1111-4111-8111-111111111111',
   now() - interval '45 days', now() - interval '80 days', now() - interval '45 days'),
  -- 2: completed, partly paid (payment reminder will appear)
  ('66666666-6666-4666-8666-666666666662', '22222222-2222-4222-8222-222222222222', '55555555-5555-4555-8555-555555555552',
   'ORD-000002', 'Лендинг Nomad Fit', 'Одностраничник с формой записи.', 250000.00, 'KZT', 'COMPLETED',
   CURRENT_DATE - 12, '11111111-1111-4111-8111-111111111113', '11111111-1111-4111-8111-111111111112',
   NULL, now() - interval '40 days', now() - interval '10 days'),
  -- 3: in progress, assigned to the employee, deadline in 2 days (deadline reminder)
  ('66666666-6666-4666-8666-666666666663', '22222222-2222-4222-8222-222222222222', '55555555-5555-4555-8555-555555555553',
   'ORD-000003', 'Фирменный стиль Flowers & Co', 'Логотип, палитра, визитки, упаковка.', 380000.00, 'KZT', 'IN_PROGRESS',
   CURRENT_DATE + 2, '11111111-1111-4111-8111-111111111113', '11111111-1111-4111-8111-111111111111',
   NULL, now() - interval '20 days', now() - interval '2 days'),
  -- 4: overdue, in progress, nobody assigned (managers get the overdue reminder)
  ('66666666-6666-4666-8666-666666666664', '22222222-2222-4222-8222-222222222222', '55555555-5555-4555-8555-555555555555',
   'ORD-000004', 'Приглашения на свадьбу', '80 штук, печать на дизайнерской бумаге.', 45000.00, 'KZT', 'IN_PROGRESS',
   CURRENT_DATE - 3, NULL, '11111111-1111-4111-8111-111111111112',
   NULL, now() - interval '15 days', now() - interval '5 days'),
  -- 5: confirmed, USD, prepaid 50%
  ('66666666-6666-4666-8666-666666666665', '22222222-2222-4222-8222-222222222222', '55555555-5555-4555-8555-555555555554',
   'ORD-000005', 'UI kit for QazDev', 'Figma library: buttons, forms, tables, dark mode.', 600.00, 'USD', 'CONFIRMED',
   CURRENT_DATE + 21, '11111111-1111-4111-8111-111111111112', '11111111-1111-4111-8111-111111111111',
   NULL, now() - interval '6 days', now() - interval '4 days'),
  -- 6: new, no payments yet
  ('66666666-6666-4666-8666-666666666666', '22222222-2222-4222-8222-222222222222', '55555555-5555-4555-8555-555555555551',
   'ORD-000006', 'Instagram Coffee Point, ноябрь', 'Контент-план и 12 постов.', 90000.00, 'KZT', 'NEW',
   CURRENT_DATE + 30, NULL, '11111111-1111-4111-8111-111111111112',
   NULL, now() - interval '1 day', now() - interval '1 day'),
  -- 7: cancelled (payment was voided), archived
  ('66666666-6666-4666-8666-666666666667', '22222222-2222-4222-8222-222222222222', '55555555-5555-4555-8555-555555555552',
   'ORD-000007', 'Баннеры для зала', 'Клиент отказался после согласования.', 60000.00, 'KZT', 'CANCELLED',
   CURRENT_DATE - 25, NULL, '11111111-1111-4111-8111-111111111111',
   now() - interval '20 days', now() - interval '35 days', now() - interval '20 days'),
  -- 8: new, deadline in 10 days
  ('66666666-6666-4666-8666-666666666668', '22222222-2222-4222-8222-222222222222', '55555555-5555-4555-8555-555555555553',
   'ORD-000008', 'Открытки к 8 марта', 'Партия 500 штук, 4 дизайна.', 150000.00, 'KZT', 'NEW',
   CURRENT_DATE + 10, '11111111-1111-4111-8111-111111111113', '11111111-1111-4111-8111-111111111111',
   NULL, now() - interval '2 hours', now() - interval '2 hours')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------- order items (line_total = quantity * unit_price)
INSERT INTO order_items (id, workspace_id, order_id, service_id, position, name, unit, quantity, unit_price, line_total) VALUES
  ('77777777-7777-4777-8777-777777777771', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666661', '44444444-4444-4444-8444-444444444441', 0, 'Дизайн логотипа', 'шт', 1.00, 120000.00, 120000.00),
  ('77777777-7777-4777-8777-777777777772', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666662', '44444444-4444-4444-8444-444444444442', 0, 'Лендинг (1 страница)', 'шт', 1.00, 250000.00, 250000.00),
  ('77777777-7777-4777-8777-777777777773', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666663', '44444444-4444-4444-8444-444444444443', 0, 'Фирменный стиль', 'шт', 1.00, 380000.00, 380000.00),
  ('77777777-7777-4777-8777-777777777774', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666664', NULL, 0, 'Приглашение, печать', 'шт', 80.00, 500.00, 40000.00),
  ('77777777-7777-4777-8777-777777777775', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666664', NULL, 1, 'Дизайн макета', 'шт', 1.00, 5000.00, 5000.00),
  ('77777777-7777-4777-8777-777777777776', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666665', '44444444-4444-4444-8444-444444444446', 0, 'UI kit (Figma)', 'шт', 1.00, 600.00, 600.00),
  ('77777777-7777-4777-8777-777777777777', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666666', '44444444-4444-4444-8444-444444444444', 0, 'Ведение Instagram', 'мес', 1.00, 90000.00, 90000.00),
  ('77777777-7777-4777-8777-777777777778', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666668', NULL, 0, 'Открытка, печать', 'шт', 500.00, 260.00, 130000.00),
  ('77777777-7777-4777-8777-777777777779', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666668', NULL, 1, 'Дизайн, 4 варианта', 'шт', 4.00, 5000.00, 20000.00)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------- payments (never more than the order total)
INSERT INTO payments (id, workspace_id, order_id, amount, payment_method, payment_date, reference, idempotency_key, created_by, created_at, voided_at, voided_by, void_reason) VALUES
  -- order 1: 50% prepayment + rest
  ('88888888-8888-4888-8888-888888888881', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666661', 60000.00, 'BANK_TRANSFER', CURRENT_DATE - 78, 'Kaspi 10234', 'a1a1a1a1-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', now() - interval '78 days', NULL, NULL, NULL),
  ('88888888-8888-4888-8888-888888888882', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666661', 60000.00, 'CARD',          CURRENT_DATE - 58, NULL,          'a1a1a1a1-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111112', now() - interval '58 days', NULL, NULL, NULL),
  -- order 2: 100 000 of 250 000 paid
  ('88888888-8888-4888-8888-888888888883', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666662', 100000.00, 'BANK_TRANSFER', CURRENT_DATE - 38, 'Halyk 55871', 'a1a1a1a1-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', now() - interval '38 days', NULL, NULL, NULL),
  -- order 3: 150 000 of 380 000
  ('88888888-8888-4888-8888-888888888884', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666663', 150000.00, 'CASH',          CURRENT_DATE - 19, NULL,          'a1a1a1a1-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111112', now() - interval '19 days', NULL, NULL, NULL),
  -- order 5: 300 of 600 USD
  ('88888888-8888-4888-8888-888888888885', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666665', 300.00,    'BANK_TRANSFER', CURRENT_DATE - 5,  'SWIFT 7781', 'a1a1a1a1-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', now() - interval '5 days', NULL, NULL, NULL),
  -- order 7: prepayment that was returned (voided), so the order could be cancelled
  ('88888888-8888-4888-8888-888888888886', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666667', 30000.00,  'CASH',          CURRENT_DATE - 33, NULL,          'a1a1a1a1-0000-4000-8000-000000000006', '11111111-1111-4111-8111-111111111112', now() - interval '33 days', now() - interval '21 days', '11111111-1111-4111-8111-111111111111', 'Возврат: клиент отказался от заказа'),
  -- this month: a few more receipts so the revenue chart has something to show
  ('88888888-8888-4888-8888-888888888887', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666662', 50000.00,  'CARD',          CURRENT_DATE - 2,  NULL,          'a1a1a1a1-0000-4000-8000-000000000007', '11111111-1111-4111-8111-111111111112', now() - interval '2 days', NULL, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------- notes
INSERT INTO order_notes (id, workspace_id, order_id, author_user_id, body, created_at) VALUES
  ('99999999-9999-4999-8999-999999999991', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666663', '11111111-1111-4111-8111-111111111113', 'Палитра согласована, делаю визитки.', now() - interval '3 days'),
  ('99999999-9999-4999-8999-999999999992', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666664', '11111111-1111-4111-8111-111111111112', 'Типография задерживает бумагу, клиент предупреждён.', now() - interval '2 days'),
  ('99999999-9999-4999-8999-999999999993', '22222222-2222-4222-8222-222222222222', '66666666-6666-4666-8666-666666666665', '11111111-1111-4111-8111-111111111111', 'Dark mode goes last, after the table components.', now() - interval '4 days')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------- activity log (what the dashboard shows as "recent")
INSERT INTO activity_logs (workspace_id, actor_user_id, action, entity_type, entity_id, metadata, created_at)
SELECT * FROM (VALUES
  ('22222222-2222-4222-8222-222222222222'::uuid, '11111111-1111-4111-8111-111111111111'::uuid, 'ORDER_CREATED',         'ORDER',  '66666666-6666-4666-8666-666666666668'::uuid, '{"orderNumber":"ORD-000008","title":"Открытки к 8 марта"}'::jsonb, now() - interval '2 hours'),
  ('22222222-2222-4222-8222-222222222222'::uuid, '11111111-1111-4111-8111-111111111112'::uuid, 'PAYMENT_RECORDED',      'ORDER',  '66666666-6666-4666-8666-666666666662'::uuid, '{"paymentId":"88888888-8888-4888-8888-888888888887","amount":"50000.00","currency":"KZT","method":"CARD"}'::jsonb, now() - interval '2 days'),
  ('22222222-2222-4222-8222-222222222222'::uuid, '11111111-1111-4111-8111-111111111113'::uuid, 'ORDER_STATUS_CHANGED',  'ORDER',  '66666666-6666-4666-8666-666666666663'::uuid, '{"orderNumber":"ORD-000003","from":"CONFIRMED","to":"IN_PROGRESS"}'::jsonb, now() - interval '2 days'),
  ('22222222-2222-4222-8222-222222222222'::uuid, '11111111-1111-4111-8111-111111111112'::uuid, 'ORDER_CREATED',         'ORDER',  '66666666-6666-4666-8666-666666666666'::uuid, '{"orderNumber":"ORD-000006","title":"Instagram Coffee Point, ноябрь"}'::jsonb, now() - interval '1 day'),
  ('22222222-2222-4222-8222-222222222222'::uuid, '11111111-1111-4111-8111-111111111111'::uuid, 'CLIENT_CREATED',        'CLIENT', '55555555-5555-4555-8555-555555555555'::uuid, '{"name":"Жанар Оспанова"}'::jsonb, now() - interval '15 days'),
  ('22222222-2222-4222-8222-222222222222'::uuid, '11111111-1111-4111-8111-111111111111'::uuid, 'PAYMENT_VOIDED',        'ORDER',  '66666666-6666-4666-8666-666666666667'::uuid, '{"paymentId":"88888888-8888-4888-8888-888888888886","amount":"30000.00","currency":"KZT","reason":"Возврат: клиент отказался от заказа"}'::jsonb, now() - interval '21 days'),
  ('22222222-2222-4222-8222-222222222222'::uuid, '11111111-1111-4111-8111-111111111111'::uuid, 'MEMBER_ADDED',          'MEMBER', '11111111-1111-4111-8111-111111111113'::uuid, '{"email":"arman@demo.clientflow.kz","role":"EMPLOYEE"}'::jsonb, now() - interval '70 days'),
  ('22222222-2222-4222-8222-222222222222'::uuid, '11111111-1111-4111-8111-111111111111'::uuid, 'MEMBER_ADDED',          'MEMBER', '11111111-1111-4111-8111-111111111112'::uuid, '{"email":"dana@demo.clientflow.kz","role":"MANAGER"}'::jsonb, now() - interval '75 days')
) AS v(workspace_id, actor_user_id, action, entity_type, entity_id, metadata, created_at)
WHERE NOT EXISTS (SELECT 1 FROM activity_logs WHERE workspace_id = '22222222-2222-4222-8222-222222222222' AND action = 'ORDER_CREATED' AND entity_id = '66666666-6666-4666-8666-666666666668');

COMMIT;
