with ignored_user_profile as (select up.person_uuid
                      from team.user_profile up
                             inner join team.user_profile_custom_role upcr on upcr.user_profile_id = up.person_id
                             inner join team.role_template_custom_role rtcr on rtcr.custom_role_id = upcr.role_id
                             inner join team.project_application pa on pa.project_id = rtcr.project_id
                      where pa.original_id in $ignoringJagaProjectIds
                      union
                      distinct
                      select up.person_uuid
                      from team.user_profile up
                             inner join team.user_group_user_profile ugup on ugup.user_profile_id = up.person_id
                             inner join team.user_profile_custom_role upcr on upcr.user_group_id = ugup.user_group_id
                             inner join team.role_template_custom_role rtcr on rtcr.custom_role_id = upcr.role_id
                             inner join team.project_application pa on pa.project_id = rtcr.project_id
                      where pa.original_id in $ignoringJagaProjectIds)
select up.id                                                  user_id,
       up.person_uuid                                         user_uuid,
       up.mail_low_case                                       email,
       up.full_name                                           user_name,
       a.id                                                   attach_id,
       (case
          when a.service_attribute = 'ICON'
            then 'icons/' || to_char(a.create_ts, 'YYYYMMDD')
          else to_char(a.create_ts, 'YYYYMMDD')
          end
          || '/' || a.attach_path || '.' || a.attach_type) as path
from jgproj.attachment a
       inner join jgproj.attachment_userinfo ausr on ausr.attachment_id = a.id
       inner join jguser.user_profile up on up.id = ausr.userinfo_id
where up.person_uuid not in (select person_uuid from ignored_user_profile)