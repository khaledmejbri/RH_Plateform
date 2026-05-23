-- Add missing 'statut' column to evaluation_template table
-- This fixes the schema mismatch where Hibernate expects the column but it doesn't exist

DO $$
BEGIN
    -- Check if the column exists before adding it
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' 
        AND column_name = 'statut'
    ) THEN
        ALTER TABLE evaluation_template 
        ADD COLUMN statut VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
        
        RAISE NOTICE 'Column statut added to evaluation_template table';
    ELSE
        RAISE NOTICE 'Column statut already exists in evaluation_template table';
    END IF;
END $$;

-- Verify the column was added
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'evaluation_template'
AND column_name = 'statut';
