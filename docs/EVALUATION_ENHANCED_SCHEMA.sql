-- =====================================================
-- ENHANCED EVALUATION MODULE - DATABASE SCHEMA
-- Clean Architecture with Scalability in Mind
-- =====================================================

-- 1. TEMPLATE TYPES (Unified approach)
CREATE TYPE template_type AS ENUM ('GENERIC', 'TECHNICAL');
CREATE TYPE template_status AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');
CREATE TYPE question_type AS ENUM (
    'TEXT',           -- Single line text
    'PARAGRAPH',      -- Multi-line text
    'MULTIPLE_CHOICE',-- Radio buttons
    'CHECKBOX',       -- Multiple selections
    'RATING',         -- Star/numeric rating
    'SCALE',          -- Likert scale (1-5, 1-10)
    'DATE',           -- Date picker
    'NUMBER'          -- Numeric input
);

-- 2. TEMPLATES TABLE (Unified for both generic and technical)
CREATE TABLE evaluation_template (
    identifiant UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom VARCHAR(255) NOT NULL,
    description TEXT,
    type template_type NOT NULL,
    statut template_status NOT NULL DEFAULT 'DRAFT',
    version INTEGER NOT NULL DEFAULT 1,
    
    -- For technical templates only
    niveau_seniorite VARCHAR(50),  -- JUNIOR, MID, SENIOR, EXPERT
    role VARCHAR(100),             -- DEVELOPPEUR, CHEF_PROJET, etc.
    domaine VARCHAR(100),          -- IT, RH, FINANCE, CHANTIER
    
    -- Metadata
    actif BOOLEAN NOT NULL DEFAULT true,
    cree_le TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP WITH TIME ZONE,
    cree_par UUID NOT NULL,        -- User ID who created
    publie_le TIMESTAMP WITH TIME ZONE,
    publie_par UUID,
    
    CONSTRAINT chk_version_positive CHECK (version > 0)
);

CREATE INDEX idx_template_type ON evaluation_template(type);
CREATE INDEX idx_template_status ON evaluation_template(statut);
CREATE INDEX idx_template_role ON evaluation_template(role) WHERE type = 'TECHNICAL';
CREATE INDEX idx_template_niveau ON evaluation_template(niveau_seniorite) WHERE type = 'TECHNICAL';

-- 3. QUESTIONS TABLE (Polymorphic - works for all template types)
CREATE TABLE evaluation_question (
    identifiant UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_identifiant UUID NOT NULL REFERENCES evaluation_template(identifiant) ON DELETE CASCADE,
    
    libelle TEXT NOT NULL,
    description TEXT,                -- Help text/instructions
    type_question question_type NOT NULL,
    ordre INTEGER NOT NULL,
    obligatoire BOOLEAN NOT NULL DEFAULT false,
    actif BOOLEAN NOT NULL DEFAULT true,
    
    -- Configuration based on question type
    options_reponses JSONB,          -- For MULTIPLE_CHOICE, CHECKBOX: ["Option 1", "Option 2"]
    valeur_minimale DECIMAL(10,2),   -- For RATING, SCALE, NUMBER
    valeur_maximale DECIMAL(10,2),   -- For RATING, SCALE, NUMBER
    unite_mesure VARCHAR(50),        -- For NUMBER: "points", "hours", etc.
    placeholder TEXT,                -- Placeholder text for input fields
    
    -- Validation rules
    regex_pattern VARCHAR(500),      -- For TEXT validation
    min_longueur INTEGER,            -- Minimum length for text
    max_longueur INTEGER,            -- Maximum length for text
    
    cree_le TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_ordre_positive CHECK (ordre > 0),
    CONSTRAINT chk_values_valid CHECK (
        (valeur_minimale IS NULL AND valeur_maximale IS NULL) OR
        (valeur_minimale < valeur_maximale)
    )
);

CREATE INDEX idx_question_template ON evaluation_question(template_identifiant);
CREATE INDEX idx_question_ordre ON evaluation_question(template_identifiant, ordre);

-- 4. CAMPAIGNS TABLE (Evaluation campaigns/periods)
CREATE TABLE evaluation_campaign (
    identifiant UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom VARCHAR(255) NOT NULL,
    description TEXT,
    
    type_evaluation VARCHAR(50) NOT NULL,  -- ANNUELLE, SEMESTRIELLE, TRIMESTRIELLE
    annee INTEGER NOT NULL,
    mois_debut INTEGER NOT NULL,
    mois_fin INTEGER NOT NULL,
    
    date_debut TIMESTAMP WITH TIME ZONE NOT NULL,
    date_fin TIMESTAMP WITH TIME ZONE NOT NULL,
    
    statut VARCHAR(30) NOT NULL DEFAULT 'PLANIFIEE',  -- PLANIFIEE, ACTIVE, TERMINEE, CANCELLED
    
    template_general_id UUID REFERENCES evaluation_template(identifiant),
    template_technique_id UUID REFERENCES evaluation_template(identifiant),
    
    -- Auto-assignment rules
    auto_assigner BOOLEAN NOT NULL DEFAULT false,
    criteres_assignment JSONB,       -- {"roles": ["DEVELOPPEUR"], "niveaux": ["SENIOR"]}
    
    cree_le TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP WITH TIME ZONE,
    cree_par UUID NOT NULL,
    
    CONSTRAINT chk_dates_valid CHECK (date_debut < date_fin),
    CONSTRAINT chk_mois_valid CHECK (mois_debet BETWEEN 1 AND 12 AND mois_fin BETWEEN 1 AND 12)
);

CREATE INDEX idx_campaign_annee ON evaluation_campaign(annee);
CREATE INDEX idx_campaign_statut ON evaluation_campaign(statut);
CREATE INDEX idx_campaign_dates ON evaluation_campaign(date_debut, date_fin);

-- 5. TEMPLATE ASSIGNMENTS (Who gets which template)
CREATE TABLE template_assignment (
    identifiant UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_identifiant UUID NOT NULL REFERENCES evaluation_campaign(identifiant) ON DELETE CASCADE,
    template_identifiant UUID NOT NULL REFERENCES evaluation_template(identifiant),
    
    -- Target criteria (can be specific or rule-based)
    collaborateur_id UUID,           -- Specific collaborator (nullable for rule-based)
    superieur_id UUID,               -- Their manager
    
    -- Rule-based assignment
    departement VARCHAR(100),        -- Department filter
    role VARCHAR(100),               -- Role filter
    niveau_seniorite VARCHAR(50),    -- Seniority filter
    site VARCHAR(100),               -- Location/site filter
    
    statut VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED',  -- ASSIGNED, COMPLETED, EXEMPTED
    date_assignment TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    date_completion TIMESTAMP WITH TIME ZONE,
    
    UNIQUE(campaign_identifiant, collaborateur_id, template_identifiant)
);

CREATE INDEX idx_assignment_campaign ON template_assignment(campaign_identifiant);
CREATE INDEX idx_assignment_collaborateur ON template_assignment(collaborateur_id);
CREATE INDEX idx_assignment_statut ON template_assignment(statut);

-- 6. EVALUATIONS (Individual evaluation instances)
CREATE TABLE evaluation (
    identifiant UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_identifiant UUID NOT NULL REFERENCES evaluation_campaign(identifiant),
    assignment_identifiant UUID NOT NULL REFERENCES template_assignment(identifiant),
    
    collaborateur_id UUID NOT NULL,
    superieur_id UUID NOT NULL,      -- Evaluator (manager)
    
    template_identifiant UUID NOT NULL REFERENCES evaluation_template(identifiant),
    
    statut VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',  
    -- NOT_STARTED, IN_PROGRESS, SUBMITTED_BY_COLLABORATOR, 
    -- UNDER_REVIEW, COMPLETED, CANCELLED
    
    etape_actuelle VARCHAR(50),      -- COLLABORATOR_INPUT, MANAGER_REVIEW, FINALIZED
    
    -- Scoring
    score_auto_calculated DECIMAL(10,2),  -- Auto-calculated from ratings
    score_final DECIMAL(10,2),            -- Final score after manager review
    commentaire_global TEXT,
    
    -- Timestamps
    date_debut TIMESTAMP WITH TIME ZONE,
    date_soumission_collaborator TIMESTAMP WITH TIME ZONE,
    date_soumission_manager TIMESTAMP WITH TIME ZONE,
    date_completion TIMESTAMP WITH TIME ZONE,
    
    cree_le TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_scores_valid CHECK (
        (score_auto_calculated IS NULL AND score_final IS NULL) OR
        (score_auto_calculated <= score_final)
    )
);

CREATE INDEX idx_evaluation_campaign ON evaluation(campaign_identifiant);
CREATE INDEX idx_evaluation_collaborateur ON evaluation(collaborateur_id);
CREATE INDEX idx_evaluation_superieur ON evaluation(superieur_id);
CREATE INDEX idx_evaluation_statut ON evaluation(statut);

-- 7. EVALUATION RESPONSES (Answers to questions)
CREATE TABLE evaluation_response (
    identifiant UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_identifiant UUID NOT NULL REFERENCES evaluation(identifiant) ON DELETE CASCADE,
    question_identifiant UUID NOT NULL REFERENCES evaluation_question(identifiant),
    
    -- Response value (polymorphic based on question type)
    reponse_texte TEXT,              -- For TEXT, PARAGRAPH
    reponse_numero DECIMAL(10,2),    -- For RATING, SCALE, NUMBER
    reponse_date DATE,               -- For DATE
    reponse_choix JSONB,             -- For MULTIPLE_CHOICE, CHECKBOX: ["choice1", "choice2"]
    
    -- Metadata
    commentaire_optionnel TEXT,      -- Additional comments
    cree_le TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP WITH TIME ZONE,
    
    UNIQUE(evaluation_identifiant, question_identifiant)
);

CREATE INDEX idx_response_evaluation ON evaluation_response(evaluation_identifiant);
CREATE INDEX idx_response_question ON evaluation_response(question_identifiant);

-- 8. EVALUATION HISTORY/AUDIT LOG
CREATE TABLE evaluation_audit_log (
    identifiant UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_identifiant UUID REFERENCES evaluation(identifiant),
    template_identifiant UUID REFERENCES evaluation_template(identifiant),
    
    action VARCHAR(50) NOT NULL,     -- CREATED, UPDATED, SUBMITTED, APPROVED, REJECTED
    acteur_id UUID NOT NULL,         -- Who performed the action
    acteur_role VARCHAR(50),         -- COLLABORATOR, MANAGER, RH, ADMIN
    
    details JSONB,                   -- Additional context
    timestamp_action TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_evaluation ON evaluation_audit_log(evaluation_identifiant);
CREATE INDEX idx_audit_timestamp ON evaluation_audit_log(timestamp_action);

-- 9. TEMPLATE VERSIONS (Track changes over time)
CREATE TABLE template_version_history (
    identifiant UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_identifiant UUID NOT NULL REFERENCES evaluation_template(identifiant) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    
    changelog TEXT,                  -- What changed in this version
    modified_by UUID NOT NULL,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    snapshot_data JSONB,             -- Full template snapshot at this version
    
    UNIQUE(template_identifiant, version)
);

CREATE INDEX idx_version_template ON template_version_history(template_identifiant, version DESC);

-- =====================================================
-- SAMPLE DATA FOR TESTING
-- =====================================================

-- Sample Generic Template
INSERT INTO evaluation_template (identifiant, nom, description, type, statut, version, cree_par)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Évaluation Annuelle Standard 2026',
    'Template standard pour évaluation annuelle de tous les collaborateurs',
    'GENERIC',
    'PUBLISHED',
    1,
    '0a4f7069-0737-4207-97dd-7a46a45f5429'
);

-- Sample questions for generic template
INSERT INTO evaluation_question (template_identifiant, libelle, type_question, ordre, obligatoire, options_reponses)
VALUES 
    ('11111111-1111-1111-1111-111111111111', 'Quels sont vos objectifs pour l''année prochaine?', 'PARAGRAPH', 1, true, NULL),
    ('11111111-1111-1111-1111-111111111111', 'Quelles sont vos principales forces?', 'PARAGRAPH', 2, true, NULL),
    ('11111111-1111-1111-1111-111111111111', 'Quels domaines nécessitent une amélioration?', 'PARAGRAPH', 3, true, NULL),
    ('11111111-1111-1111-1111-111111111111', 'De quelles réalisations êtes-vous le plus fier?', 'PARAGRAPH', 4, false, NULL),
    ('11111111-1111-1111-1111-111111111111', 'Évaluez votre satisfaction globale au travail', 'RATING', 5, true, NULL),
    ('11111111-1111-1111-1111-111111111111', 'Quelles difficultés avez-vous rencontrées?', 'CHECKBOX', 6, false, '["Manque de ressources", "Problèmes de communication", "Charge de travail excessive", "Manque de formation"]');

-- Sample Technical Template for Senior Backend
INSERT INTO evaluation_template (identifiant, nom, description, type, statut, version, niveau_seniorite, role, domaine, cree_par)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'Évaluation Technique - Développeur Senior Backend',
    'Évaluation des compétences techniques pour développeurs backend senior',
    'TECHNICAL',
    'PUBLISHED',
    1,
    'SENIOR',
    'DEVELOPPEUR_BACKEND',
    'IT',
    '0a4f7069-0737-4207-97dd-7a46a45f5429'
);

-- Sample technical questions
INSERT INTO evaluation_question (template_identifiant, libelle, description, type_question, ordre, obligatoire, valeur_minimale, valeur_maximale)
VALUES 
    ('22222222-2222-2222-2222-222222222222', 'Maîtrise de Java avancé', 'Concepts avancés: streams, lambdas, concurrency', 'RATING', 1, true, 1, 5),
    ('22222222-2222-2222-2222-222222222222', 'Expertise Spring Boot', 'Microservices, security, data, cloud', 'RATING', 2, true, 1, 5),
    ('22222222-2222-2222-2222-222222222222', 'Connaissance Kubernetes', 'Déploiement, orchestration, monitoring', 'RATING', 3, false, 1, 5),
    ('22222222-2222-2222-2222-222222222222', 'Architecture microservices', 'Design patterns, API design, event-driven', 'RATING', 4, true, 1, 5),
    ('22222222-2222-2222-2222-222222222222', 'Qualité du code et bonnes pratiques', 'Clean code, testing, refactoring', 'RATING', 5, true, 1, 5);
