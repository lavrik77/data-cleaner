with ignored_user_profile as (select up.person_uuid
                                from team.user_profile up
                          inner join team.user_profile_custom_role upcr on upcr.user_profile_id = up.person_id
                          inner join team.role_template_custom_role rtcr on rtcr.custom_role_id = upcr.role_id
                          inner join team.project_application pa on pa.project_id = rtcr.project_id
                               where pa.original_id in $ignoringJagaProjectIds
                               union distinct
                              select up.person_uuid
                                from team.user_profile up
                          inner join team.user_group_user_profile ugup on ugup.user_profile_id = up.person_id
                          inner join team.user_profile_custom_role upcr on upcr.user_group_id = ugup.user_group_id
                          inner join team.role_template_custom_role rtcr on rtcr.custom_role_id = upcr.role_id
                          inner join team.project_application pa on pa.project_id = rtcr.project_id
                               where pa.original_id in $ignoringJagaProjectIds)
select person_id, person_uuid, full_name, email
  from team.user_profile
 where person_id > 1002 and person_uuid not in (select person_uuid from ignored_user_profile)