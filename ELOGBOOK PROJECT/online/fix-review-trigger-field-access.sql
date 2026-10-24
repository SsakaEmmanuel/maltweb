-- Fix E-Logbook review-protection trigger field access.
-- The same trigger function is used by daily_entries and weekly_summaries.
-- Only reference fields that exist on the current trigger table.
CREATE OR REPLACE FUNCTION public.protect_logbook_review_fields()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE
  v_user uuid := auth.uid();
  v_weekly boolean := TG_TABLE_NAME = 'weekly_summaries';
  v_daily boolean := TG_TABLE_NAME = 'daily_entries';
BEGIN
  IF v_user IS NULL THEN
    RETURN NEW;
  END IF;

  IF NOT v_weekly AND NOT v_daily THEN
    RAISE EXCEPTION 'Review protection is not configured for table %.', TG_TABLE_NAME;
  END IF;

  IF NEW.id IS DISTINCT FROM OLD.id
     OR NEW.student_id IS DISTINCT FROM OLD.student_id
     OR NEW.placement_id IS DISTINCT FROM OLD.placement_id
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'Report identity and ownership cannot be changed.';
  END IF;

  IF v_user = OLD.student_id THEN
    IF NEW.supervisor_comments IS DISTINCT FROM OLD.supervisor_comments
       OR NEW.supervisor_signed_at IS DISTINCT FROM OLD.supervisor_signed_at THEN
      RAISE EXCEPTION 'Only an assigned supervisor can edit review fields.';
    END IF;

    -- Keep weekly-only and daily-only fields in separate PL/pgSQL branches.
    IF v_weekly THEN
      IF NEW.week_start IS DISTINCT FROM OLD.week_start
         OR NEW.summary IS DISTINCT FROM OLD.summary
         OR NEW.skills_learned IS DISTINCT FROM OLD.skills_learned
         OR NEW.challenges IS DISTINCT FROM OLD.challenges
         OR NEW.client_updated_at IS DISTINCT FROM OLD.client_updated_at THEN
        NEW.supervisor_comments := '';
        NEW.supervisor_signed_at := NULL;
      END IF;
    ELSE
      IF NEW.entry_date IS DISTINCT FROM OLD.entry_date
         OR NEW.work_done IS DISTINCT FROM OLD.work_done
         OR NEW.tools_used IS DISTINCT FROM OLD.tools_used
         OR NEW.skills_learned IS DISTINCT FROM OLD.skills_learned
         OR NEW.challenges IS DISTINCT FROM OLD.challenges
         OR NEW.recommendations IS DISTINCT FROM OLD.recommendations
         OR NEW.client_updated_at IS DISTINCT FROM OLD.client_updated_at THEN
        NEW.supervisor_comments := '';
        NEW.supervisor_signed_at := NULL;
      END IF;
    END IF;
  ELSE
    IF v_weekly THEN
      IF NEW.week_start IS DISTINCT FROM OLD.week_start
         OR NEW.summary IS DISTINCT FROM OLD.summary
         OR NEW.skills_learned IS DISTINCT FROM OLD.skills_learned
         OR NEW.challenges IS DISTINCT FROM OLD.challenges
         OR NEW.client_updated_at IS DISTINCT FROM OLD.client_updated_at THEN
        RAISE EXCEPTION 'Supervisors may only update review fields.';
      END IF;
    ELSE
      IF NEW.entry_date IS DISTINCT FROM OLD.entry_date
         OR NEW.work_done IS DISTINCT FROM OLD.work_done
         OR NEW.tools_used IS DISTINCT FROM OLD.tools_used
         OR NEW.skills_learned IS DISTINCT FROM OLD.skills_learned
         OR NEW.challenges IS DISTINCT FROM OLD.challenges
         OR NEW.recommendations IS DISTINCT FROM OLD.recommendations
         OR NEW.client_updated_at IS DISTINCT FROM OLD.client_updated_at THEN
        RAISE EXCEPTION 'Supervisors may only update review fields.';
      END IF;
    END IF;
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END;
$$;
