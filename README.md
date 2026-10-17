# Tadpoles

The current application uses one USD ledger, Fauxnance market data, internally booked trades and cash movements. Start with the [setup and operating guide](impDocs/Frontend/ClientPageImplementation/ClientRefactorCrashCourse.MD) and [verification evidence](impDocs/Frontend/ClientPageImplementation/ClientRefactorCommentary.MD). Broader UK/India, FX and crypto trading is explicitly deferred.

Default local startup is `docker compose up --build -d` after configuring `.env` from `.env.template` with provider credentials. Do not overwrite an existing environment file or delete retained database volumes.

## Team Member Names
- Devon: Dates
- Grace: Grapes
- Michael: Mayo
- Timothy: Tacos
- Justin: Jalapenos


## Team Git Workflow Choice
For our project, we opted to use Git-Flow Development, for the purpose of maintaining a clean and organized codebase. This workflow allows us to manage feature development, bug fixes, and releases in a structured manner.

We will impose a rule that all pull requests must be reviewed by at least one other team member and pass all Jenkins testing within pipeline.

## Historical role overview

Current scoped permissions are documented in the guide above; ADMIN does not bypass Trading Operations instrument controls.
## Historical role overview

Current scoped permissions are documented in the guide above; ADMIN does not bypass Trading Operations instrument controls.

# Admin
- Administer users and manage system settings.
- Access to all data within the application.

# Auditor
- View and analyze data for auditing purposes.
- Access to audit logs and reports.

# Analyst
- View analytics dashboard for user activity and trends.

# Advisor
- Can manage the portfolio of their assigned clients.
- View client data and provide recommendations.

# Client
- Access to their own data and portfolio information.
- View reports and recommendations provided by their advisor.

## Database Integration Testing
- Schema under test: `database/fresh/001_schema.sql` plus the retained upgrade migrations
- Schema under test: `database/fresh/001_schema.sql` plus the retained upgrade migrations
- Test suite location: `testing/src/test/java/com/neueda/leap/db`
- Local run (requires Docker daemon for Testcontainers):
  - `cd testing && mvn -B test`
- Containerized run:
  - `docker compose --profile testing run --rm testing`
