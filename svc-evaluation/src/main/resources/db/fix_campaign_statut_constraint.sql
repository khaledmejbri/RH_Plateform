-- Fix evaluation_campaign_statut_check constraint
-- Add ACTIVE to the allowed values

DO $$
DECLARE
    constraint_exists BOOLEAN;
BEGIN
    -- Check if the constraint exists
    SELECT EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE table_name = 'evaluation_campaign' 
        AND constraint_name = 'evaluation_campaign_statut_check'
    ) INTO constraint_exists;

    IF constraint_exists THEN
        -- Drop the old constraint
        ALTER TABLE evaluation_campaign DROP CONSTRAINT evaluation_campaign_statut_check;
        RAISE NOTICE 'Dropped old evaluation_campaign_statut_check constraint';
    END IF;

    -- Add new constraint with all valid EvaluationCampaignStatus values
    ALTER TABLE evaluation_campaign 
    ADD CONSTRAINT evaluation_campaign_statut_check 
    CHECK (statut IN ('PLANIFIEE', 'ACTIVE', 'TERMINEE', 'ANNULEE'));

    RAISE NOTICE 'Created new evaluation_campaign_statut_check constraint with all valid values including ACTIVE';
END $$;

-- Verify the constraint
SELECT 
    tc.constraint_name,
    tc.table_name,
    cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc 
    ON tc.constraint_name = cc.constraint_name
WHERE tc.table_name = 'evaluation_campaign'
AND tc.constraint_type = 'CHECK'
AND tc.constraint_name = 'evaluation_campaign_statut_check';
