# 🚀 Enhanced Evaluation Module - Implementation Script
# Run this step-by-step to implement the enhanced system

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Enhanced Evaluation Module Installer" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$PROJECT_ROOT = "C:\Local\Khaled\project"
$EVALUATION_SVC = "$PROJECT_ROOT\svc-evaluation"
$ADMIN_WEB = "$PROJECT_ROOT\rh-admin-web"

Write-Host "Step 1: Database Migration" -ForegroundColor Yellow
Write-Host "Running database schema migration..." -ForegroundColor Gray
Write-Host "Please execute manually:" -ForegroundColor Yellow
Write-Host "  psql -U postgres -d your_database -f $PROJECT_ROOT\docs\EVALUATION_ENHANCED_SCHEMA.sql" -ForegroundColor White
Write-Host ""

Write-Host "Step 2: Backend Entities Created ✓" -ForegroundColor Green
Write-Host "  - TemplateType enum" -ForegroundColor Gray
Write-Host "  - TemplateStatus enum" -ForegroundColor Gray  
Write-Host "  - QuestionType enum (updated)" -ForegroundColor Gray
Write-Host "  - EvaluationTemplate entity (updated)" -ForegroundColor Gray
Write-Host ""

Write-Host "Next Steps to Complete:" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Create DTOs:" -ForegroundColor Yellow
Write-Host "   Location: $EVALUATION_SVC\src\main\java\com\hr\evaluation\dto\" -ForegroundColor Gray
Write-Host "   Files needed:" -ForegroundColor Gray
Write-Host "   - CreateTemplateRequest.java" -ForegroundColor Gray
Write-Host "   - CreateQuestionRequest.java" -ForegroundColor Gray
Write-Host "   - TemplateResponse.java" -ForegroundColor Gray
Write-Host "   - QuestionResponse.java" -ForegroundColor Gray
Write-Host ""

Write-Host "2. Update Repositories:" -ForegroundColor Yellow
Write-Host "   Location: $EVALUATION_SVC\src\main\java\com\hr\evaluation\repository\" -ForegroundColor Gray
Write-Host "   Add methods:" -ForegroundColor Gray
Write-Host "   - findByTypeAndStatut()" -ForegroundColor Gray
Write-Host "   - findByIdWithQuestions()" -ForegroundColor Gray
Write-Host "   - findByRoleAndNiveauSeniorite()" -ForegroundColor Gray
Write-Host ""

Write-Host "3. Create/Update Services:" -ForegroundColor Yellow
Write-Host "   Location: $EVALUATION_SVC\src\main\java\com\hr\evaluation\service\" -ForegroundColor Gray
Write-Host "   Files:" -ForegroundColor Gray
Write-Host "   - TemplateService.java (enhanced)" -ForegroundColor Gray
Write-Host "   - EvaluationWorkflowService.java (new)" -ForegroundColor Gray
Write-Host "   - AssignmentService.java (new)" -ForegroundColor Gray
Write-Host ""

Write-Host "4. Update Controllers:" -ForegroundColor Yellow
Write-Host "   Location: $EVALUATION_SVC\src\main\java\com\hr\evaluation\web\" -ForegroundColor Gray
Write-Host "   Files:" -ForegroundColor Gray
Write-Host "   - TemplateAdminController.java (enhanced)" -ForegroundColor Gray
Write-Host "   - EvaluationController.java (updated)" -ForegroundColor Gray
Write-Host ""

Write-Host "5. Frontend Dependencies:" -ForegroundColor Yellow
Write-Host "   Run in $ADMIN_WEB:" -ForegroundColor Gray
Write-Host "   npm install @hello-pangea/dnd" -ForegroundColor White
Write-Host ""

Write-Host "6. Frontend Components:" -ForegroundColor Yellow
Write-Host "   Location: $ADMIN_WEB\src\components\" -ForegroundColor Gray
Write-Host "   Create:" -ForegroundColor Gray
Write-Host "   - TemplateBuilder.tsx" -ForegroundColor Gray
Write-Host "   - EvaluationFormRenderer.tsx" -ForegroundColor Gray
Write-Host "   - AssignmentManager.tsx" -ForegroundColor Gray
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Documentation Available:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1. Database Schema: docs\EVALUATION_ENHANCED_SCHEMA.sql" -ForegroundColor White
Write-Host "2. Implementation Guide: EVALUATION_ENHANCED_IMPLEMENTATION.md" -ForegroundColor White
Write-Host "3. Feature Summary: EVALUATION_ADMIN_COMPLETE.md" -ForegroundColor White
Write-Host ""

Write-Host "Would you like me to continue creating the backend files? (Y/N)" -ForegroundColor Yellow
$response = Read-Host

if ($response -eq 'Y' -or $response -eq 'y') {
    Write-Host ""
    Write-Host "Continuing with DTO creation..." -ForegroundColor Green
    # This will be handled in next steps
} else {
    Write-Host "Implementation paused. Review documentation and run manually." -ForegroundColor Yellow
}
