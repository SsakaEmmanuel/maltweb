# MALTWEB v1 — Foundation

MALTWEB is being rebuilt as a local digital ecosystem:
**Discover → Connect → Interact**

## Stack
- PHP 8+
- SQLite
- HTML/CSS/JavaScript
- No framework required for the foundation

## Run with XAMPP
1. Copy the `maltweb` folder into `htdocs`.
2. Start Apache in XAMPP.
3. Open `http://localhost/maltweb/setup.php` once.
4. Then open `http://localhost/maltweb/`.

## Account roles
- user
- business
- service_provider
- admin (database-ready; admin UI comes in a later phase)

## Next development phase
- Location hierarchy
- Listing/profile creation
- Business/service-provider dashboards
- Better search and filtering
- Events and opportunities
- Admin dashboard
- Verification and reporting
- Favourites and notifications

## Admin panel
Admin routes are protected by the logged-in session role. Public registration cannot create an admin account. An existing trusted admin account must be promoted directly in the database during development, then use `/admin/`.


## v5 — Events & Opportunities
- Public event discovery at `events.php`
- Public opportunity discovery at `opportunities.php`
- Logged-in users can submit events and opportunities
- New submissions start as `pending`
- Admin moderation at `/admin/events.php` and `/admin/opportunities.php`
- Setup now performs a backward-compatible events migration


## v6 — Account Ecosystem
- Role-aware dashboards for users, businesses and service providers
- Admin dashboard routing
- Favourites / saved profiles
- Creator statistics for profiles, events and opportunities
- Public profile save button


## v7 — Connect Layer
- Direct profile contact / messaging
- User inbox
- Notifications
- Admin profile verification controls
- Verification notifications
