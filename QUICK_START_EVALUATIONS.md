# 🚀 Quick Start: Evaluation Management Admin Panel

## Step 1: Start Backend Services

```powershell
# Terminal 1 - Start svc-evaluation
cd C:\Local\Khaled\project\svc-evaluation
mvn spring-boot:run

# Wait for: "Started EvaluationApplication"
```

## Step 2: Start Admin Web Application

```powershell
# Terminal 2 - Start admin web
cd C:\Local\Khaled\project\rh-admin-web
npm run dev

# Opens: http://localhost:5173
```

## Step 3: Login and Navigate

1. Open browser: `http://localhost:5173`
2. Login with admin credentials
3. Click **"Évaluations"** in the left sidebar
4. You'll see 4 tabs:
   - 📅 **Campagnes** - Create and manage evaluation campaigns
   - 📝 **Templates Généraux** - Manage question templates
   - 🔧 **Templates Techniques** - Manage technical skill templates  
   - 📊 **Évaluations** - Monitor all evaluations

## Step 4: Create Your First Campaign

1. Go to **Campagnes** tab
2. Click **"+ Nouvelle campagne"**
3. Fill the form:
   - **Nom**: `Évaluation Test 2026`
   - **Type**: Annuelle
   - **Année**: 2026
   - **Mois début**: Juin
   - **Mois fin**: Juin
4. Click **"Créer la campagne"**
5. Click **"Activer"** to make it active

## Step 5: Monitor Evaluations

1. Go to **Évaluations** tab
2. See statistics:
   - Total evaluations
   - Validated count
   - Pending count
3. Filter by campaign name
4. View detailed evaluation table

## 🔑 API Endpoints Available

### Campaigns
- `POST /api/rh/v1/evaluations/campaigns` - Create campaign
- `POST /api/rh/v1/evaluations/campaigns/{id}/activate` - Activate
- `POST /api/rh/v1/evaluations/campaigns/{id}/terminate` - Terminate

### Templates
- `POST /api/rh/v1/evaluations/templates` - Create template
- `GET /api/rh/v1/evaluations/templates/{id}/questions` - View questions

### Evaluations
- `GET /api/rh/v1/evaluations` - List all evaluations
- `GET /api/rh/v1/evaluations/{id}` - Get by ID

## 📝 Notes

- Campaigns can only be created for **June (6)** or **December (12)**
- Activation only works during the campaign period
- Scheduler creates evaluations every **2 minutes** (for testing)
- All data is persisted in PostgreSQL

## 🐛 Troubleshooting

### Backend not starting?
```powershell
# Check if PostgreSQL is running
# Check port 8080 is available
netstat -ano | findstr :8080
```

### Frontend not loading?
```powershell
# Install dependencies
npm install

# Clear cache
npm run dev -- --force
```

### API 401 errors?
- Make sure you're logged in
- Check token in browser DevTools → Application → Local Storage

## 📚 Documentation

- **Full Implementation Guide**: [EVALUATION_ADMIN_IMPLEMENTATION.md](./EVALUATION_ADMIN_IMPLEMENTATION.md)
- **Scheduler Setup**: [svc-evaluation/EVALUATION_SCHEDULER_SETUP.md](./svc-evaluation/EVALUATION_SCHEDULER_SETUP.md)
- **Mobile App**: [rh_mobile_app/EVALUATION_FEATURE_IMPLEMENTATION.md](./rh_mobile_app/EVALUATION_FEATURE_IMPLEMENTATION.md)

---

**Ready to use! 🎉**
