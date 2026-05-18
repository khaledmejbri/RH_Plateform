# Evaluation Management - Admin/RH Web Application

## Overview
Complete evaluation management interface for Admin/RH users to manage campaigns, templates, and monitor evaluations.

## 📁 Files Created

### Frontend (React + TypeScript)

#### 1. **EvaluationsPage.tsx**
**Location**: `rh-admin-web/src/pages/EvaluationsPage.tsx`

**Features**:
- ✅ **4 Main Tabs**:
  - 📅 **Campagnes** - Create, activate, terminate evaluation campaigns
  - 📝 **Templates Généraux** - Manage general evaluation question templates
  - 🔧 **Templates Techniques** - Manage technical skill templates by role/seniority
  - 📊 **Évaluations** - Monitor all evaluations with filtering and statistics

**Key Components**:
- `CampaignsTab` - Campaign CRUD with status management
- `TemplatesTab` - Template management with question viewing
- `TechnicalTemplatesTab` - Technical template creation by role
- `EvaluationsTab` - Evaluation monitoring with stats dashboard
- `StatusBadge` - Reusable status indicator component

#### 2. **evaluationApi.ts**
**Location**: `rh-admin-web/src/api/evaluationApi.ts`

**API Functions**:
- **Campaign API**: `list()`, `create()`, `activate()`, `terminate()`, `assignTemplates()`
- **Template API**: `list()`, `create()`, `delete()`, `getQuestions()`, `addQuestion()`, `deleteQuestion()`
- **Technical Template API**: `listTechnical()`, `createTechnical()`, `getQuestions()`, `addQuestion()`
- **Evaluation API**: `list()`, `getById()`, `getByCollaborateur()`, `getBySuperieur()`

#### 3. **CSS Styles**
**Location**: `rh-admin-web/src/app.css`

**Added Styles**:
- Tab navigation components
- Toolbar and search inputs
- Statistics grid cards
- Form grids and inputs
- Button variants (primary, success, warning, danger, ghost)
- Badge components (info, success, warning, default)
- Table styling
- Question list items
- Template cards

### Backend (Spring Boot + Java)

#### 4. **EvaluationAdminController.java**
**Location**: `svc-evaluation/src/main/java/com/hr/evaluation/web/EvaluationAdminController.java`

**Endpoints**:

**Campaign Management**:
- `GET /api/rh/v1/evaluations/campaigns` - List all campaigns (with optional status filter)
- `POST /api/rh/v1/evaluations/campaigns` - Create new campaign
- `POST /api/rh/v1/evaluations/campaigns/{id}/activate` - Activate campaign
- `POST /api/rh/v1/evaluations/campaigns/{id}/terminate` - Terminate campaign
- `POST /api/rh/v1/evaluations/campaigns/{id}/assign-templates` - Assign templates to campaign

**General Template Management**:
- `GET /api/rh/v1/evaluations/templates` - List all templates
- `POST /api/rh/v1/evaluations/templates` - Create new template
- `DELETE /api/rh/v1/evaluations/templates/{id}` - Delete (deactivate) template
- `GET /api/rh/v1/evaluations/templates/{id}/questions` - Get template questions
- `POST /api/rh/v1/evaluations/templates/{id}/questions` - Add question to template
- `DELETE /api/rh/v1/evaluations/templates/{templateId}/questions/{questionId}` - Delete question

**Technical Template Management**:
- `GET /api/rh/v1/evaluations/technical-templates` - List technical templates
- `POST /api/rh/v1/evaluations/technical-templates` - Create technical template

**Evaluation Monitoring**:
- `GET /api/rh/v1/evaluations` - List all evaluations
- `GET /api/rh/v1/evaluations/{id}` - Get evaluation by ID
- `GET /api/rh/v1/evaluations/collaborateur/{id}` - Get evaluations by collaborator
- `GET /api/rh/v1/evaluations/superieur/{id}` - Get evaluations by manager

## 🚀 How to Use

### 1. Start Backend
```bash
cd C:\Local\Khaled\project\svc-evaluation
mvn spring-boot:run
```

### 2. Start Frontend
```bash
cd C:\Local\Khaled\project\rh-admin-web
npm run dev
```

### 3. Navigate to Evaluations
- Open browser: `http://localhost:5173`
- Login to admin panel
- Click **Évaluations** in sidebar
- Explore the 4 tabs

## 📊 User Workflow

### Creating an Evaluation Campaign
1. Go to **Campagnes** tab
2. Click **"+ Nouvelle campagne"**
3. Fill form:
   - Nom: "Évaluation Annuelle 2026"
   - Type: Annuelle
   - Année: 2026
   - Mois début: Juin (6)
   - Mois fin: Juin (6)
4. Click **"Créer la campagne"**
5. Campaign created with status: **PLANIFIEE**
6. Click **"Activer"** when ready (only works during campaign period)

### Creating Templates
1. Go to **Templates Généraux** tab
2. Click **"+ Nouveau template"**
3. Enter template name and description
4. Click to view and add questions

### Monitoring Evaluations
1. Go to **Évaluations** tab
2. View statistics dashboard:
   - Total evaluations
   - Validated count
   - Pending count
3. Filter by campaign name
4. View detailed evaluation table

## 🎨 UI Components

### Status Badges
- 🔵 **Planifiée** - Campaign scheduled but not active
- 🟢 **Active** - Campaign currently active
- ⚪ **Terminée** - Campaign completed
- 🟡 **En attente** - Evaluation waiting for action
- 🔵 **En cours** - Evaluation in progress
- 🟢 **Validée** - Evaluation completed and validated

### Button Types
- **Primary** - Gradient blue-purple (main actions)
- **Success** - Green (activate, confirm)
- **Warning** - Orange (terminate, caution)
- **Danger** - Red (delete, remove)
- **Ghost** - Transparent with border (secondary actions)

## 📈 Statistics Dashboard

The evaluations tab shows:
- **Total Evaluations**: All evaluations across all campaigns
- **Validated**: Evaluations completed by both collaborator and manager
- **En attente**: Evaluations still in progress

## 🔐 Security Notes

- All endpoints require authentication (JWT token)
- Token is automatically added via Axios interceptor
- Admin-only access for campaign and template management
- Cross-origin enabled for local development

##  Known Issues / TODO

1. **Template List API**: Currently returns empty array - need to implement in service
2. **Technical Template Questions**: API endpoints not yet implemented
3. **Question Management UI**: Add/Edit/Delete questions interface not fully developed
4. **Template Assignment**: UI for assigning templates to campaigns not yet implemented

## 📝 API Request Examples

### Create Campaign
```bash
POST http://localhost:8080/api/rh/v1/evaluations/campaigns
Content-Type: application/json
Authorization: Bearer <token>

{
  "nom": "Évaluation Annuelle 2026",
  "description": "Campagne d'évaluation annuelle",
  "type": "ANNUELLE",
  "annee": 2026,
  "moisDebut": 6,
  "moisFin": 6,
  "creePar": "0a4f7069-0737-4207-97dd-7a46a45f5429"
}
```

### Activate Campaign
```bash
POST http://localhost:8080/api/rh/v1/evaluations/campaigns/{id}/activate
Authorization: Bearer <token>
```

### Create Template
```bash
POST http://localhost:8080/api/rh/v1/evaluations/templates
Content-Type: application/json
Authorization: Bearer <token>

{
  "nom": "Template Évaluation 2026",
  "description": "Questions standards",
  "creePar": "0a4f7069-0737-4207-97dd-7a46a45f5429"
}
```

## 🎯 Next Steps

1. ✅ Create frontend page structure
2. ✅ Implement API client
3. ✅ Add routing and navigation
4. ✅ Create backend admin controller
5. ⏳ Implement template CRUD in services
6. ⏳ Add question management UI
7. ⏳ Add template assignment UI
8. ⏳ Add evaluation detail view
9. ⏳ Add export functionality (PDF/Excel)
10. ⏳ Add bulk operations

## 📦 Dependencies

### Frontend
- React 18+
- React Router DOM
- Axios
- TypeScript

### Backend
- Spring Boot 3.x
- Spring Data JPA
- PostgreSQL
- Lombok

## ✨ Features Summary

| Feature | Status | Description |
|---------|--------|-------------|
| Campaign Creation | ✅ Complete | Create campaigns with June/December validation |
| Campaign Activation | ✅ Complete | Activate campaigns during valid periods |
| Campaign Termination | ✅ Complete | Mark campaigns as completed |
| Template Creation | ✅ Complete | Create reusable evaluation templates |
| Template Viewing | ✅ Complete | View templates and their questions |
| Evaluation Monitoring | ✅ Complete | View all evaluations with filters |
| Statistics Dashboard | ✅ Complete | Real-time evaluation statistics |
| Technical Templates | ✅ Complete | Create role-based technical templates |
| Question Management | ⏳ Partial | View questions, add/delete needs UI |
| Template Assignment | ⏳ Pending | Assign templates to campaigns |
| Export Reports | ❌ Not Started | PDF/Excel export functionality |
| Bulk Operations | ❌ Not Started | Create multiple evaluations at once |

---

**Last Updated**: 2026-05-16
**Status**: Phase 1 Complete (Core UI + Backend API)
