-- Fix evaluation_template table - Remove duplicate 'name' column
-- The entity uses 'nom' column, but database has both 'nom' and 'name'

-- Step 1: Check if both columns exist
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'evaluation_template' 
AND column_name IN ('nom', 'name');

-- Step 2: Drop the 'name' column (we use 'nom' instead)
ALTER TABLE evaluation_template DROP COLUMN IF EXISTS name;

-- Step 3: Make sure 'nom' column has correct constraints
ALTER TABLE evaluation_template ALTER COLUMN nom SET NOT NULL;

-- Step 4: Verify the fix
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'evaluation_template';
