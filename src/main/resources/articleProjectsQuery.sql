WITH object_data AS (
  SELECT id, created_date, extension, original_name
  FROM dblink(
           'hostaddr=$dbHost port=$dbPort dbname=$dbName user=$dbUser password=$dbPassword',
           'SELECT id, created_date, extension, original_name FROM objects'
       ) AS o(id VARCHAR, created_date TIMESTAMP, extension VARCHAR, original_name TEXT)
),
     all_file_uuids AS (
       -- UUID иконок пространств
       SELECT s.title,
              s.jaga_project_id,
              REPLACE(s.icon, '/api/icons/', '') as file_uuid,
              'icons/' as path_prefix
       FROM space s
       WHERE s.jaga_project_id NOT IN $ignoringJagaProjectIds
         AND s.icon IS NOT NULL

       UNION
       -- UUID файлов из вложений и истории вложений
       SELECT s.title,
              s.jaga_project_id,
              REPLACE(uri, '/api/files/', '') as file_uuid,
              '' as path_prefix
       FROM attachment a
              LEFT JOIN space s ON a.space_id = s.id
       WHERE s.jaga_project_id NOT IN $ignoringJagaProjectIds
         AND a.uri IS NOT NULL
       UNION
       SELECT s.title,
              s.jaga_project_id,
              REPLACE(ah.uri, '/api/files/', '') as file_uuid,
              '' as path_prefix
       FROM attachment_history ah
              LEFT JOIN attachment a ON ah.attachment_id = a.id
              LEFT JOIN space s ON a.space_id = s.id
       WHERE s.jaga_project_id NOT IN $ignoringJagaProjectIds
         AND ah.uri IS NOT NULL
     )
SELECT DISTINCT
  afu.jaga_project_id,
  afu.title,
  od.original_name,
  afu.path_prefix || TO_CHAR(od.created_date, 'YYYYMMDD') || '/' || afu.file_uuid || '.' || od.extension as path
FROM all_file_uuids afu
       INNER JOIN object_data od ON od.id = afu.file_uuid
ORDER BY afu.jaga_project_id, od.original_name