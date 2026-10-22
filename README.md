# WEALTH — Smart Wealth Management System

A responsive personal-finance MVP for a Java Data Structures mini-project.

## Architecture
- Frontend: React + Vite, TypeScript, Supabase JS Auth, Recharts
- Backend: Java 17 + Spring Boot REST API
- Database: Supabase PostgreSQL with Row Level Security
- Auth: Supabase Auth; the frontend sends its access token to Java. The Java API verifies the token against Supabase Auth's `/auth/v1/user` endpoint before accessing data.
- Data structures: academic mapping is documented in Settings/About. Database operations themselves are not represented as Java in-memory data structures.

## 1. Supabase setup
1. Create a Supabase project.
2. Open **SQL Editor**, run `supabase/schema.sql`.
3. In Project Settings → API, copy the Project URL and anon/publishable key.
4. Copy `frontend/.env.example` to `frontend/.env` and fill in:
   - `VITE_SUPABASE_URL`
   - `VITE_SUPABASE_ANON_KEY`
   - `VITE_API_URL=http://localhost:8080`
5. Copy `backend/src/main/resources/application-example.yml` to `backend/src/main/resources/application.yml` and set `supabase.url` and `supabase.anon-key`.
6. Never put a Supabase service-role key in the frontend or commit secrets.

## 2. Run Java backend
Requires JDK 17+ and Maven.
```bash
cd backend
mvn spring-boot:run
```
The backend runs on `http://localhost:8080`.

## 3. Run frontend
Requires Node.js 20+.
```bash
cd frontend
npm install
npm run dev
```
Open the local URL printed by Vite.

## 4. Demo data
After signing up and logging in, open Settings → Demo Data and click **Load demo data**. This inserts realistic Indian-rupee transactions, investments, and goals into the signed-in user's Supabase account. You can delete the records normally afterward.

## MVP coverage
- Supabase sign-up, sign-in, sign-out and protected application shell
- Dashboard summaries and charts from database records
- Investment CRUD, search, filters and client-side sorting
- Transaction CRUD, search/filter and undo-last-action (session-local stack)
- Goal CRUD, progress calculations and priority ordering
- Expense analysis and category aggregation
- Monthly and yearly reports computed from stored transactions
- Assets/liabilities and net-worth calculations
- Settings/profile and academic data-structure mapping

## Notes
- The app is a financial tracker, not financial advice, a trading service, or a bank integration.
- Monetary values are stored as PostgreSQL `numeric`; the UI formats them with `en-IN` grouping and INR currency.
- Backend ownership is enforced by validating the Supabase access token and using the authenticated user's ID in all queries. RLS is also enabled in the schema.
- This is a project MVP, not a production financial service. Before public deployment, add rate limiting, stronger operational logging, automated tests, backup policies and a reviewed token-validation strategy.
