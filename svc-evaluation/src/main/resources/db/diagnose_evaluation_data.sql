-- Diagnostic script to check evaluation and campaign data
-- Run this to see if evaluations have proper campaign and template associations

-- Check all evaluations with their campaigns
SELECT 
    e.identifiant as evaluation_id,
    e.collaborateur_identifiant,
    e.etape_actuelle,
    e.campaign_identifiant,
    c.nom as campaign_name,
    c.type as campaign_type,
    c.template_general_identifiant,
    c.template_technique_identifiant,
    c.template_competence_identifiant
FROM evaluation e
LEFT JOIN evaluation_campaign c ON e.campaign_identifiant = c.identifiant
ORDER BY e.cree_le DESC;

-- Check if templates exist
SELECT 
    t.identifiant,
    t.nom,
    t.type,
    t.actif
FROM evaluation_template t
WHERE t.actif = true;

-- Check technical templates
SELECT 
    t.identifiant,
    t.nom,
    t.actif
FROM technical_template t
WHERE t.actif = true;

-- Count questions per template
SELECT 
    'evaluation_template' as template_type,
    t.identifiant,
    t.nom,
    COUNT(q.identifiant) as question_count
FROM evaluation_template t
LEFT JOIN evaluation_question q ON q.template_identifiant = t.identifiant AND q.actif = true
WHERE t.actif = true
GROUP BY t.identifiant, t.nom

UNION ALL

SELECT 
    'technical_template' as template_type,
    t.identifiant,
    t.nom,
    COUNT(q.identifiant) as question_count
FROM technical_template t
LEFT JOIN technical_question q ON q.template_identifiant = t.identifiant AND q.actif = true
WHERE t.actif = true
GROUP BY t.identifiant, t.nom;
