select distinct p.id                                                as project_id,
                p.title,
                a.id                                                as attachment_id,
                a.attach_name                                       as original_name,
                (case
                   when a.service_attribute = 'ICON'
                     then 'icons/' || to_char(a.create_ts, 'YYYYMMDD')
                   else to_char(a.create_ts, 'YYYYMMDD')
                   end
                   || '/' || a.attach_path || '.' || a.attach_type) as path
from proj.attachment a
       join proj.attachment_project aprj on aprj.attachment_id = a.id
       join proj.project as p on p.id = aprj.project_id
where aprj.project_id not in $ignoringJagaProjectIds
union all
select distinct p.id                                                as project_id,
                p.title,
                a.id                                                as attachment_id,
                a.attach_name                                       as original_name,
                (case
                   when a.service_attribute = 'ICON'
                     then 'icons/' || to_char(a.create_ts, 'YYYYMMDD')
                   else to_char(a.create_ts, 'YYYYMMDD')
                   end
                   || '/' || a.attach_path || '.' || a.attach_type) as path
from proj.attachment a
       join proj.attachment_task atsk on atsk.attachment_id = a.id
       join proj.task t on t.id = atsk.task_id
       join proj.project p on p.id = t.project_id
where t.project_id not in $ignoringJagaProjectIds
order by jaga_project_id, original_name