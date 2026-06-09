select p.id                as jaga_project_id,
       p.uuid              as jaga_project_uuid,
       p.title             as jaga_project_name,
       p.mcode             as jaga_project_mcode,
       ps.koschey_space_id as articles_space_id
from jgproj.project p
       left join jgintegr_kosch.project_space ps on ps.jaga_project_id = p.id
where p.is_template = false
  and p.id not in $ignoringJagaProjectIds
order by p.id $direction