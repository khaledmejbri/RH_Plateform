# 🚀 Enhanced Evaluation Module - Quick Start Guide

## ✅ What's Been Implemented

### Backend (Spring Boot) - svc-evaluation

#### 1. **Domain Enums Created** ✓
- `TemplateType.java` - GENERIC / TECHNICAL
- `TemplateStatus.java` - DRAFT / PUBLISHED / ARCHIVED  
- `QuestionType.java` - Updated with 8 types (TEXT, PARAGRAPH, MULTIPLE_CHOICE, CHECKBOX, RATING, SCALE, DATE, NUMBER)

#### 2. **Entities Updated** ✓
- `EvaluationTemplate.java` - Enhanced with:
  - Unified template type support
  - Status tracking (DRAFT/PUBLISHED/ARCHIVED)
  - Version control
  - Technical template fields (niveauSeniorite, role, domaine)
  - Questions relationship
  - Publication tracking

### Documentation Created ✓
1. **Database Schema**: `docs/EVALUATION_ENHANCED_SCHEMA.sql`
   - 9 normalized tables
   - Complete with indexes and constraints
   - Sample data included
   
2. **Implementation Guide**: `EVALUATION_ENHANCED_IMPLEMENTATION.md`
   - Full backend code examples
   - Frontend component examples
   - Security model
   - Deployment checklist

3. **Implementation Script**: `IMPLEMENT_EVALUATION_MODULE.ps1`
   - Step-by-step PowerShell script
   - Interactive guidance

---

## 📋 Next Steps - Manual Implementation Required

### Phase 1: Database Setup (5 minutes)

```powershell
# Run the database migration
cd C:\Local\Khaled\project
psql -U postgres -d your_database_name -f docs\EVALUATION_ENHANCED_SCHEMA.sql
```

**What this does:**
- Creates all enhanced tables
- Adds indexes for performance
- Inserts sample templates and questions

---

### Phase 2: Backend DTOs (15 minutes)

Create these files in `svc-evaluation/src/main/java/com/hr/evaluation/dto/`:

#### 1. `CreateTemplateRequest.java`
```java
package com.hr.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CreateTemplateRequest {
    @NotBlank private String nom;
    private String description;
    @NotNull private String type; // GENERIC or TECHNICAL
    private String niveauSeniorite;
    private String role;
    private String domaine;
    private List<CreateQuestionRequest> questions;
    
    // Add getters and setters
}
```

#### 2. `CreateQuestionRequest.java`
```java
package com.hr.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public class CreateQuestionRequest {
    @NotBlank private String libelle;
    private String description;
    @NotNull private String typeQuestion;
    @NotNull private Integer ordre;
    private Boolean obligatoire = false;
    private List<String> optionsReponses;
    private BigDecimal valeurMinimale;
    private BigDecimal valeurMaximale;
    private String uniteMesure;
    private String placeholder;
    
    // Add getters and setters
}
```

---

### Phase 3: Update Repository (10 minutes)

Update `EvaluationTemplateRepository.java`:

```java
package com.hr.evaluation.repository;

import com.hr.evaluation.domain.TemplateStatus;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.entity.EvaluationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface EvaluationTemplateRepository extends JpaRepository<EvaluationTemplate, UUID> {
    
    List<EvaluationTemplate> findByType(TemplateType type);
    List<EvaluationTemplate> findByStatut(TemplateStatus statut);
    List<EvaluationTemplate> findByTypeAndStatut(TemplateType type, TemplateStatus statut);
    List<EvaluationTemplate> findAllByActifTrue();
    
    @Query("SELECT t FROM EvaluationTemplate t LEFT JOIN FETCH t.questions WHERE t.id = :id")
    Optional<EvaluationTemplate> findByIdWithQuestions(@Param("id") UUID id);
    
    List<EvaluationTemplate> findByRoleAndNiveauSeniorite(String role, String niveauSeniorite);
}
```

---

### Phase 4: Enhance Service Layer (20 minutes)

Create `TemplateService.java` (see full implementation in `EVALUATION_ENHANCED_IMPLEMENTATION.md`)

Key methods:
- `creerTemplate()` - Create template with questions
- `publierTemplate()` - Publish draft template
- `archiverTemplate()` - Archive template
- `ajouterQuestion()` - Add question to template
- `supprimerQuestion()` - Delete question
- `reorderQuestions()` - Reorder via drag-and-drop
- `listerTemplates()` - List with filters

---

### Phase 5: Update Controller (15 minutes)

Update `EvaluationAdminController.java` with new endpoints:

```java
@PostMapping
@PreAuthorize("hasAnyRole('RH', 'ADMIN')")
public ResponseEntity<EvaluationTemplate> createTemplate(
        @Valid @RequestBody CreateTemplateRequest request,
        @RequestParam UUID userId) {
    // Implementation
}

@GetMapping("/{templateId}")
public ResponseEntity<EvaluationTemplate> getTemplate(@PathVariable UUID templateId) {
    // Implementation
}

@PostMapping("/{templateId}/publish")
public ResponseEntity<EvaluationTemplate> publishTemplate(
        @PathVariable UUID templateId,
        @RequestParam UUID userId) {
    // Implementation
}

@PostMapping("/{templateId}/questions/reorder")
public ResponseEntity<Void> reorderQuestions(
        @PathVariable UUID templateId,
        @RequestBody List<UUID> questionIdsInOrder) {
    // Implementation
}
```

---

### Phase 6: Frontend Dependencies (2 minutes)

```powershell
cd C:\Local\Khaled\project\rh-admin-web
npm install @hello-pangea/dnd
```

---

### Phase 7: Frontend Components (30 minutes)

Create these components in `rh-admin-web/src/components/`:

1. **TemplateBuilder.tsx** - Drag-and-drop template builder
2. **EvaluationFormRenderer.tsx** - Dynamic form renderer
3. **AssignmentManager.tsx** - Template assignment UI

(Full code available in `EVALUATION_ENHANCED_IMPLEMENTATION.md`)

---

### Phase 8: Update API Client (10 minutes)

Update `rh-admin-web/src/api/evaluationApi.ts` with new methods:

```typescript
export const templateApi = {
  async create(data: CreateTemplateRequest): Promise<EvaluationTemplate> {
    const response = await api.post('/api/rh/v1/admin/evaluations/templates', data);
    return response.data;
  },

  async publish(id: string, userId: string): Promise<EvaluationTemplate> {
    const response = await api.post(
      `/api/rh/v1/admin/evaluations/templates/${id}/publish?userId=${userId}`
    );
    return response.data;
  },

  async reorderQuestions(templateId: string, questionIds: string[]): Promise<void> {
    await api.post(
      `/api/rh/v1/admin/evaluations/templates/${templateId}/questions/reorder`,
      questionIds
    );
  }
};
```

---

## 🧪 Testing Checklist

After implementation, test:

- [ ] Create generic template with multiple question types
- [ ] Create technical template with role/seniority
- [ ] Drag-and-drop to reorder questions
- [ ] Publish template
- [ ] Assign template to campaign
- [ ] Complete evaluation as collaborator
- [ ] Review evaluation as manager
- [ ] View evaluation history
- [ ] Archive old template

---

## 📊 Estimated Time

| Task | Time |
|------|------|
| Database Migration | 5 min |
| DTOs Creation | 15 min |
| Repository Update | 10 min |
| Service Layer | 20 min |
| Controller Update | 15 min |
| Frontend Dependencies | 2 min |
| Frontend Components | 30 min |
| API Client Update | 10 min |
| Testing | 30 min |
| **Total** | **~2.5 hours** |

---

## 🎯 Current Status

✅ **Completed:**
- Domain enums (3 files)
- Enhanced entity (EvaluationTemplate)
- Database schema (SQL file)
- Complete documentation
- Implementation script

⏳ **Pending:**
- DTOs (4 files)
- Repository methods
- Service layer enhancements
- Controller updates
- Frontend components
- API client updates

---

## 🚦 Quick Commands

```powershell
# 1. Run database migration
psql -U postgres -d rh_database -f C:\Local\Khaled\project\docs\EVALUATION_ENHANCED_SCHEMA.sql

# 2. Install frontend dependency
cd C:\Local\Khaled\project\rh-admin-web
npm install @hello-pangea/dnd

# 3. Restart backend
cd C:\Local\Khaled\project\svc-evaluation
mvn clean install
mvn spring-boot:run

# 4. Start frontend
cd C:\Local\Khaled\project\rh-admin-web
npm run dev
```

---

## 📚 Reference Documentation

All detailed code examples are in:
- **Backend**: `EVALUATION_ENHANCED_IMPLEMENTATION.md` (Lines 1-800)
- **Frontend**: `EVALUATION_ENHANCED_IMPLEMENTATION.md` (Lines 800-1308)
- **Database**: `docs/EVALUATION_ENHANCED_SCHEMA.sql`

---

## 💡 Tips

1. **Start with database** - Run migration first
2. **Test incrementally** - Test each endpoint as you build it
3. **Use Postman** - Test APIs before connecting frontend
4. **Check logs** - Monitor backend logs for errors
5. **Browser DevTools** - Check Network tab for API calls

---

**Ready to continue? Let me know which phase you'd like me to implement next!** 🚀
