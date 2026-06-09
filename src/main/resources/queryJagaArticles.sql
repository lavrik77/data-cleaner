-- select a.file_name, a.uri
-- from public.attachment a
--          left join public.space s on a.space_id = s.id
-- where s.jaga_project_id in (1008556)
-- ;
--
-- select u.first_name, u.last_name, image_url
-- from public.suz_user u
--          left join user_to_space us on u.id = us.user_id
--          left join public.space s on us.space_id = s.id
-- where s.jaga_project_id in (1008556)
-- ;
--
-- select s.id, s.jaga_project_id, s.title, s.icon
-- from public.space s
-- where s.jaga_project_id in (1008556)
-- ;
--
-- select ah.uri, ah.file_name
-- from public.attachment_history ah
--          join public.attachment a on ah.attachment_id = a.id
--          left join public.space s on a.space_id = s.id
-- where s.jaga_project_id in (1008556)
-- ;
--
-- select o.original_name
-- from public.objects o
-- where o.file_name in ('')


--TODO: написать скрипт для запроса к статьям...
-- тут удалить надо будет наши вложения из их таблиц.
-- думается, что проще это будет сделать, заджойнив наши проекты с их пространствами, а затем снести вложения уже по space_id

-- kernel_test_1:attachment - здесь лежат вложения.
-- их необходимо будет чистить
-- чтобы создать полный путь к файлу в MINIO, нужно сделать префикс в виде даты (20251014)
-- и добавить uuid из поля URI (ddb0c3ff-ce6b-438c-8900-e29d0dbdbe10 из /api/files/ddb0c3ff-ce6b-438c-8900-e29d0dbdbe10)

-- kernel_test_1:attachment_history - здесь история изменения вложений.
-- ее тоже надо будет чистить.
-- найти изменения можно по связи с табл.attachment

-- storage_test1:objects - здесь инфа о всех файлах, лежащих в MINIO
-- чистим.
-- здесь хранятся и вложения, и иконки, и аватарки, но без нормальной привязки к таблицам, где содержится инфа об этих файлах.

-- вопрос в том, что есть еще таблицы, файлы из которых лежат в objects и в minio,
-- и эти таблицы тож надо будет почистить. Проблема в том, что связи прямой нет - надо будет поискать их.
-- Вот, что я уже нашел:
--      kernel_test_1:suz_user, предположительно в поле image_url можно будет найти данные
--      kernel_test_1:space, в поле icon (но далеко не во всех строках) есть ссылка на картинку, как в objects


-----FINAL-----
--настройки берутся из vault для test_1
--SPRING_DATASOURCE_PASSWORD kernel
--SPRING_DATASOURCE_URL jdbc:postgresql://10.42.126.144:5432/storage_test1
--SPRING_DATASOURCE_USERNAME kernel
WITH object_data AS (
    SELECT id, created_date, extension, original_name
    FROM dblink(
                 'hostaddr=10.42.126.144 port=5432 dbname=storage_test1 user=kernel password=kernel',
                 'SELECT id, created_date, extension, original_name FROM objects'
         ) AS o(id VARCHAR, created_date TIMESTAMP, extension VARCHAR, original_name TEXT)
),
     all_file_uuids AS (
         -- UUID иконок пользователей и пространств
         SELECT s.title,
                s.jaga_project_id,
                REPLACE(su.image_url, '/api/icons/', '') as file_uuid,
                'icons/' as path_prefix
         FROM suz_user su
                  LEFT JOIN user_to_space uts ON su.id = uts.user_id
                  LEFT JOIN space s ON s.id = uts.space_id
         WHERE s.jaga_project_id  IN (1060, 1009, 1000759, 1000637)
           AND su.image_url IS  NULL
         UNION
         SELECT s.title,
                s.jaga_project_id,
                REPLACE(s.icon, '/api/icons/', '') as file_uuid,
                'icons/' as path_prefix
         FROM space s
         WHERE s.jaga_project_id  IN (1060, 1009, 1000759, 1000637)
           AND s.icon IS NOT NULL

         UNION
         -- UUID файлов из вложений и истории вложений
         SELECT s.title,
                s.jaga_project_id,
                REPLACE(uri, '/api/files/', '') as file_uuid,
                '' as path_prefix
         FROM attachment a
                  LEFT JOIN space s ON a.space_id = s.id
         WHERE s.jaga_project_id  IN (1060, 1009, 1000759, 1000637)
           AND a.uri IS NOT NULL
         UNION
         SELECT s.title,
                s.jaga_project_id,
                REPLACE(ah.uri, '/api/files/', '') as file_uuid,
                '' as path_prefix
         FROM attachment_history ah
                  LEFT JOIN attachment a ON ah.attachment_id = a.id
                  LEFT JOIN space s ON a.space_id = s.id
         WHERE s.jaga_project_id  IN (1060, 1009, 1000759, 1000637)
           AND ah.uri IS NOT NULL
     )
SELECT DISTINCT
    afu.title,
    afu.jaga_project_id,
    afu.file_uuid,
    afu.path_prefix || TO_CHAR(od.created_date, 'YYYYMMDD') || '/' ||
    afu.file_uuid || '.' || od.extension as path,
    od.original_name
FROM all_file_uuids afu
         INNER JOIN object_data od ON od.id = afu.file_uuid;


-----FINAL-----урезанный
WITH all_file_uuids AS (
    -- UUID иконок пользователей и пространств
    SELECT REPLACE(su.image_url, '/api/icons/', '') as file_uuid
    FROM suz_user su
             LEFT JOIN user_to_space uts ON su.id = uts.user_id
             LEFT JOIN space s ON s.id = uts.space_id
    WHERE s.jaga_project_id NOT IN (1008556, 1008368, 1000759, 1000637)
      AND su.image_url IS NOT NULL
    UNION
    SELECT REPLACE(s.icon, '/api/icons/', '') as file_uuid
    FROM space s
    WHERE s.jaga_project_id NOT IN (1008556, 1008368, 1000759, 1000637)
      AND s.icon IS NOT NULL

    UNION
    -- UUID файлов из вложений и истории вложений
    SELECT REPLACE(uri, '/api/files/', '') as file_uuid
    FROM attachment a
             LEFT JOIN space s ON a.space_id = s.id
    WHERE s.jaga_project_id NOT IN (1008556, 1008368, 1000759, 1000637)
      AND a.uri IS NOT NULL
    UNION
    SELECT REPLACE(ah.uri, '/api/files/', '') as file_uuid
    FROM attachment_history ah
             LEFT JOIN attachment a ON ah.attachment_id = a.id
             LEFT JOIN space s ON a.space_id = s.id
    WHERE s.jaga_project_id NOT IN (1008556, 1008368, 1000759, 1000637)
      AND ah.uri IS NOT NULL)
SELECT DISTINCT afu.file_uuid
FROM all_file_uuids afu;