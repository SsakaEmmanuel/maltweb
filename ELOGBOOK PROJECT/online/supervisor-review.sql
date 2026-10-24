-- Supervisor review permissions for the dedicated E-Logbook Supabase project.
-- Run only in project qjzftbgahxkolljpmkmn. The same migration has already been
-- applied to the connected project. Supervisor identities must be provisioned
-- by a trusted administrator and assignment rows must be created by trusted staff.

DROP POLICY IF EXISTS "assigned supervisor reads assigned student profile" ON public.profiles;
CREATE POLICY "assigned supervisor reads assigned student profile"
ON public.profiles FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    WHERE sa.student_id = profiles.id AND sa.supervisor_id = (SELECT auth.uid())
  )
);

DROP POLICY IF EXISTS "assigned supervisors read weekly summaries" ON public.weekly_summaries;
CREATE POLICY "assigned supervisors read weekly summaries"
ON public.weekly_summaries FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = weekly_summaries.student_id
      AND sa.supervisor_id = (SELECT auth.uid())
      AND (
        (sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor')
      )
  )
);

DROP POLICY IF EXISTS "assigned supervisors update weekly review" ON public.weekly_summaries;
CREATE POLICY "assigned supervisors update weekly review"
ON public.weekly_summaries FOR UPDATE TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = weekly_summaries.student_id
      AND sa.supervisor_id = (SELECT auth.uid())
      AND (
        (sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor')
      )
  )
)
WITH CHECK (
  EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = weekly_summaries.student_id
      AND sa.supervisor_id = (SELECT auth.uid())
      AND (
        (sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor')
      )
  )
);

DROP POLICY IF EXISTS "assigned supervisors read daily entries" ON public.daily_entries;
CREATE POLICY "assigned supervisors read daily entries"
ON public.daily_entries FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = daily_entries.student_id
      AND sa.supervisor_id = (SELECT auth.uid())
      AND (
        (sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor')
      )
  )
);

DROP POLICY IF EXISTS "assigned supervisors update daily review" ON public.daily_entries;
CREATE POLICY "assigned supervisors update daily review"
ON public.daily_entries FOR UPDATE TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = daily_entries.student_id
      AND sa.supervisor_id = (SELECT auth.uid())
      AND (
        (sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor')
      )
  )
)
WITH CHECK (
  EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = daily_entries.student_id
      AND sa.supervisor_id = (SELECT auth.uid())
      AND (
        (sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor')
      )
  )
);

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

DROP TRIGGER IF EXISTS protect_weekly_summary_review_fields ON public.weekly_summaries;
CREATE TRIGGER protect_weekly_summary_review_fields
BEFORE UPDATE ON public.weekly_summaries
FOR EACH ROW EXECUTE FUNCTION public.protect_logbook_review_fields();

DROP TRIGGER IF EXISTS protect_daily_entry_review_fields ON public.daily_entries;
CREATE TRIGGER protect_daily_entry_review_fields
BEFORE UPDATE ON public.daily_entries
FOR EACH ROW EXECUTE FUNCTION public.protect_logbook_review_fields();



-- Consolidate own-record and assigned-reviewer access into a single policy per
-- operation so the database only evaluates one permissive policy per action.
DROP POLICY IF EXISTS "profile owner reads own profile" ON public.profiles;
DROP POLICY IF EXISTS "assigned supervisor reads assigned student profile" ON public.profiles;
CREATE POLICY "owners and assigned supervisors read profiles"
ON public.profiles FOR SELECT TO authenticated
USING (
  id = (SELECT auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    WHERE sa.student_id = profiles.id AND sa.supervisor_id = (SELECT auth.uid())
  )
);

DROP POLICY IF EXISTS "student reads own daily entries" ON public.daily_entries;
DROP POLICY IF EXISTS "assigned supervisors read daily entries" ON public.daily_entries;
CREATE POLICY "students and assigned supervisors read daily entries"
ON public.daily_entries FOR SELECT TO authenticated
USING (
  student_id = (SELECT auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = daily_entries.student_id AND sa.supervisor_id = (SELECT auth.uid())
      AND ((sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor'))
  )
);

DROP POLICY IF EXISTS "student updates own daily entries" ON public.daily_entries;
DROP POLICY IF EXISTS "assigned supervisors update daily review" ON public.daily_entries;
CREATE POLICY "students and assigned supervisors update daily entries"
ON public.daily_entries FOR UPDATE TO authenticated
USING (
  student_id = (SELECT auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = daily_entries.student_id AND sa.supervisor_id = (SELECT auth.uid())
      AND ((sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor'))
  )
)
WITH CHECK (
  student_id = (SELECT auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = daily_entries.student_id AND sa.supervisor_id = (SELECT auth.uid())
      AND ((sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor'))
  )
);

DROP POLICY IF EXISTS "student reads own weekly summaries" ON public.weekly_summaries;
DROP POLICY IF EXISTS "assigned supervisors read weekly summaries" ON public.weekly_summaries;
CREATE POLICY "students and assigned supervisors read weekly summaries"
ON public.weekly_summaries FOR SELECT TO authenticated
USING (
  student_id = (SELECT auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = weekly_summaries.student_id AND sa.supervisor_id = (SELECT auth.uid())
      AND ((sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor'))
  )
);

DROP POLICY IF EXISTS "student updates own weekly summaries" ON public.weekly_summaries;
DROP POLICY IF EXISTS "assigned supervisors update weekly review" ON public.weekly_summaries;
CREATE POLICY "students and assigned supervisors update weekly summaries"
ON public.weekly_summaries FOR UPDATE TO authenticated
USING (
  student_id = (SELECT auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = weekly_summaries.student_id AND sa.supervisor_id = (SELECT auth.uid())
      AND ((sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor'))
  )
)
WITH CHECK (
  student_id = (SELECT auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.supervisor_assignments sa
    JOIN public.profiles reviewer ON reviewer.id = (SELECT auth.uid())
    WHERE sa.student_id = weekly_summaries.student_id AND sa.supervisor_id = (SELECT auth.uid())
      AND ((sa.assignment_type = 'field_supervisor' AND reviewer.role = 'field_supervisor')
        OR (sa.assignment_type = 'institution_supervisor' AND reviewer.role = 'institution_supervisor'))
  )
);
