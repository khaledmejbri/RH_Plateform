-- Comprehensive schema fix for evaluation service
-- This script adds all missing columns to ensure database matches entity definitions

-- ============================================
-- 1. evaluation_template table
-- ============================================

DO $$
BEGIN
    -- Add statut column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' AND column_name = 'statut'
    ) THEN
        ALTER TABLE evaluation_template ADD COLUMN statut VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
        RAISE NOTICE 'Added statut to evaluation_template';
    END IF;

    -- Add version column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' AND column_name = 'version'
    ) THEN
        ALTER TABLE evaluation_template ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
        RAISE NOTICE 'Added version to evaluation_template';
    END IF;

    -- Add niveau_seniorite column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' AND column_name = 'niveau_seniorite'
    ) THEN
        ALTER TABLE evaluation_template ADD COLUMN niveau_seniorite VARCHAR(50);
        RAISE NOTICE 'Added niveau_seniorite to evaluation_template';
    END IF;

    -- Add role column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' AND column_name = 'role'
    ) THEN
        ALTER TABLE evaluation_template ADD COLUMN role VARCHAR(100);
        RAISE NOTICE 'Added role to evaluation_template';
    END IF;

    -- Add domaine column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' AND column_name = 'domaine'
    ) THEN
        ALTER TABLE evaluation_template ADD COLUMN domaine VARCHAR(100);
        RAISE NOTICE 'Added domaine to evaluation_template';
    END IF;

    -- Add actif column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' AND column_name = 'actif'
    ) THEN
        ALTER TABLE evaluation_template ADD COLUMN actif BOOLEAN NOT NULL DEFAULT TRUE;
        RAISE NOTICE 'Added actif to evaluation_template';
    END IF;

    -- Add cree_par column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' AND column_name = 'cree_par'
    ) THEN
        ALTER TABLE evaluation_template ADD COLUMN cree_par UUID;
        RAISE NOTICE 'Added cree_par to evaluation_template';
    END IF;

    -- Add publie_le column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' AND column_name = 'publie_le'
    ) THEN
        ALTER TABLE evaluation_template ADD COLUMN publie_le TIMESTAMP WITH TIME ZONE;
        RAISE NOTICE 'Added publie_le to evaluation_template';
    END IF;

    -- Add publie_par column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_template' AND column_name = 'publie_par'
    ) THEN
        ALTER TABLE evaluation_template ADD COLUMN publie_par UUID;
        RAISE NOTICE 'Added publie_par to evaluation_template';
    END IF;
END $$;

-- ============================================
-- 2. evaluation_campaign table
-- ============================================

DO $$
BEGIN
    -- Add template_competence_identifiant column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_campaign' AND column_name = 'template_competence_identifiant'
    ) THEN
        ALTER TABLE evaluation_campaign ADD COLUMN template_competence_identifiant UUID;
        RAISE NOTICE 'Added template_competence_identifiant to evaluation_campaign';
    END IF;
END $$;

-- ============================================
-- 3. evaluation_question table
-- ============================================

DO $$
BEGIN
    -- Add section_code column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'section_code'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN section_code VARCHAR(80);
        RAISE NOTICE 'Added section_code to evaluation_question';
    END IF;

    -- Add section_libelle column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'section_libelle'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN section_libelle VARCHAR(255);
        RAISE NOTICE 'Added section_libelle to evaluation_question';
    END IF;

    -- Add poids column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'poids'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN poids NUMERIC(19,2) DEFAULT 1;
        RAISE NOTICE 'Added poids to evaluation_question';
    END IF;

    -- Add labels_echelle column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'labels_echelle'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN labels_echelle TEXT;
        RAISE NOTICE 'Added labels_echelle to evaluation_question';
    END IF;

    -- Add valeur_minimale column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'valeur_minimale'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN valeur_minimale NUMERIC(19,2);
        RAISE NOTICE 'Added valeur_minimale to evaluation_question';
    END IF;

    -- Add valeur_maximale column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'valeur_maximale'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN valeur_maximale NUMERIC(19,2);
        RAISE NOTICE 'Added valeur_maximale to evaluation_question';
    END IF;

    -- Add unite_mesure column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'unite_mesure'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN unite_mesure VARCHAR(50);
        RAISE NOTICE 'Added unite_mesure to evaluation_question';
    END IF;

    -- Add placeholder column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'placeholder'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN placeholder VARCHAR(500);
        RAISE NOTICE 'Added placeholder to evaluation_question';
    END IF;

    -- Add regex_pattern column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'regex_pattern'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN regex_pattern VARCHAR(500);
        RAISE NOTICE 'Added regex_pattern to evaluation_question';
    END IF;

    -- Add min_longueur column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'min_longueur'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN min_longueur INTEGER;
        RAISE NOTICE 'Added min_longueur to evaluation_question';
    END IF;

    -- Add max_longueur column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'max_longueur'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN max_longueur INTEGER;
        RAISE NOTICE 'Added max_longueur to evaluation_question';
    END IF;

    -- Add cree_par column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_question' AND column_name = 'cree_par'
    ) THEN
        ALTER TABLE evaluation_question ADD COLUMN cree_par UUID;
        RAISE NOTICE 'Added cree_par to evaluation_question';
    END IF;
END $$;

-- ============================================
-- 4. evaluation_answer table
-- ============================================

DO $$
BEGIN
    -- Add note_collaborateur column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_answer' AND column_name = 'note_collaborateur'
    ) THEN
        ALTER TABLE evaluation_answer ADD COLUMN note_collaborateur INTEGER;
        RAISE NOTICE 'Added note_collaborateur to evaluation_answer';
    END IF;

    -- Add note_manager column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'evaluation_answer' AND column_name = 'note_manager'
    ) THEN
        ALTER TABLE evaluation_answer ADD COLUMN note_manager INTEGER;
        RAISE NOTICE 'Added note_manager to evaluation_answer';
    END IF;
END $$;

-- ============================================
-- 5. technical_template table
-- ============================================

DO $$
BEGIN
    -- Add nom column if missing (entity uses name field but column should be nom)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'technical_template' AND column_name = 'nom'
    ) THEN
        ALTER TABLE technical_template ADD COLUMN nom VARCHAR(255);
        RAISE NOTICE 'Added nom to technical_template';
    END IF;

    -- Add role_metier column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'technical_template' AND column_name = 'role_metier'
    ) THEN
        ALTER TABLE technical_template ADD COLUMN role_metier VARCHAR(100);
        RAISE NOTICE 'Added role_metier to technical_template';
    END IF;

    -- Add departement column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'technical_template' AND column_name = 'departement'
    ) THEN
        ALTER TABLE technical_template ADD COLUMN departement VARCHAR(100);
        RAISE NOTICE 'Added departement to technical_template';
    END IF;

    -- Add cree_par column if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'technical_template' AND column_name = 'cree_par'
    ) THEN
        ALTER TABLE technical_template ADD COLUMN cree_par UUID;
        RAISE NOTICE 'Added cree_par to technical_template';
    END IF;
END $$;

-- ============================================
-- Verification queries
-- ============================================

SELECT 
    table_name, 
    column_name, 
    data_type, 
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = 'public'
AND table_name IN (
    'evaluation_template',
    'evaluation_campaign',
    'evaluation_question',
    'evaluation_answer',
    'technical_template'
)
ORDER BY table_name, ordinal_position;
