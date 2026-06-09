with object_data as (select id, created_date, extension, original_name
                     from dblink(
                              'hostaddr=$dbHost port=$dbPort dbname=$dbName user=$dbUser password=$dbPassword',
                              'select id, created_date, extension, original_name from objects'
                          ) as o(id varchar, created_date timestamp, extension varchar, original_name text)),
     -- Игнорируемые пользователи
     ignored_user_ids as (select su.id
                          from suz_user su
                                 inner join user_to_space uts on su.id = uts.user_id
                                 inner join space s on s.id = uts.space_id
                          where s.project_id in $ignoringJagaProjectIds),
     -- UUID удаляемых иконок пользователей
     del_file_uuids as (select su.id                                    as user_id,
                               su.email                                 as email,
                               concat_ws(' ',
                                         su.last_name,
                                         su.first_name,
                                         su.middle_name)                as user_name,
                               replace(su.image_url, '/api/icons/', '') as file_uuid,
                               'icons/'                                 as path_prefix
                        from suz_user su
                        where su.image_url is not null
                          and su.id not in (select id from ignored_user_ids))
select distinct dfu.user_id,
                dfu.user_name,
                dfu.email,
                dfu.path_prefix || to_char(od.created_date, 'yyyymmdd') || '/' || dfu.file_uuid || '.' ||
                od.extension as path
from del_file_uuids dfu
       inner join object_data od on od.id = dfu.file_uuid