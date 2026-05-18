-- Fix evaluation_campaign type check constraint
-- This script updates the CHECK constraint to accept the correct enum values

-- Step 1: Drop the existing constraint
ALTER TABLE evaluation_campaign DROP CONSTRAINT IF EXISTS evaluation_campaign_type_check;

-- Step 2: Add the correct constraint with proper enum values
ALTER TABLE evaluation_campaign 
ADD CONSTRAINT evaluation_campaign_type_check 
CHECK (type IN ('ANNUELLE', 'SEMESTRIELLE'));

-- Verify the constraint
SELECT conname, consrc 
FROM pg_constraint 
WHERE conrelid = 'evaluation_campaign'::regclass 
AND contype = 'c';
