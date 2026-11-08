---
name: StudyMind AI Design System
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#3e4947'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#6e7977'
  outline-variant: '#bdc9c6'
  surface-tint: '#006a63'
  primary: '#005c55'
  on-primary: '#ffffff'
  primary-container: '#0f766e'
  on-primary-container: '#a3faef'
  inverse-primary: '#80d5cb'
  secondary: '#515f74'
  on-secondary: '#ffffff'
  secondary-container: '#d5e3fc'
  on-secondary-container: '#57657a'
  tertiary: '#734700'
  on-tertiary: '#ffffff'
  tertiary-container: '#945d00'
  on-tertiary-container: '#ffe6cc'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#9cf2e8'
  primary-fixed-dim: '#80d5cb'
  on-primary-fixed: '#00201d'
  on-primary-fixed-variant: '#00504a'
  secondary-fixed: '#d5e3fc'
  secondary-fixed-dim: '#b9c7df'
  on-secondary-fixed: '#0d1c2e'
  on-secondary-fixed-variant: '#3a485b'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb95f'
  on-tertiary-fixed: '#2a1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  display-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 52px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Be Vietnam Pro
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Be Vietnam Pro
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  title-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 26px
  title-md:
    fontFamily: Be Vietnam Pro
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: Be Vietnam Pro
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
  body-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Be Vietnam Pro
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Be Vietnam Pro
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Be Vietnam Pro
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.03em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-tablet: 1.5rem
  gutter-desktop: 1.5rem
  margin: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.25rem
  space-xl: 1.5rem
---

## Brand & Style

This design system blends **Material Design 3 (Material You)** principles with a focused, tactile, and calm academic aesthetic tailored for Vietnamese high school and university students. The experience balances AI-driven intelligence with cognitive clarity, rejecting sensory overload in favor of deep-work enablement.

### Personality & Emotional Tenor
- **Focused & Grounded:** Deep Teal and Slate tones suppress mental fatigue during extended study sessions.
- **Rewarding & Gamified:** Warm Amber accents emphasize discipline (streaks, study milestones, Pro tier incentives) without feeling frivolous.
- **Empathetic & Precision-Driven:** Typography tailored specifically for the Vietnamese tonal diacritics ensures flawless legibility across dense academic prompts, formulas, and flashcards.
- **Form Factor:** Android-first native paradigms following Material 3 spatial logic, dynamic tonal layering, and ergonomic reachability.

## Colors

The color system establishes high chromatic clarity across day and night study modes while adhering strictly to M3 role mapping.

### Light Mode Architecture
- **Primary:** `#0F766E` (Teal 700) – Anchor for primary actions, active navigation states, and key interactive landmarks.
- **On-Primary:** `#FFFFFF`
- **Primary Container:** `#CCFBF1` (Teal 100) – Subtle backings for conversational bubbles and highlighted topic tags.
- **On-Primary Container:** `#115E59` (Teal 800)
- **Secondary:** `#475569` (Slate 600) – Contextual icons, secondary button fills, and non-critical metrics.
- **Surface Canvas:** `#F8FAFC` (Slate 50) – Ambient background providing contrast for elevated layers.
- **Surface Container (Card Surface):** `#FFFFFF` – Primary elevated content canvas.

### Accent Roles
- **Reward / Gamification (Amber):**
  - Accent Amber: `#F59E0B` – StudyCoin counters, streak flames, Pro subscription badges.
  - Accent Amber Container: `#FEF3C7`
  - On-Amber Container: `#78350F`
- **Semantic Statuses:**
  - Success Green: `#16A34A` – Mastery indicators, correct quiz validations.
  - Success Container: `#DCFCE7` / On-Success Container: `#14532D`
  - Destructive / Error Rose: `#E11D48` – Incorrect answers, warnings, deletion actions.
  - Error Container: `#FFE4E6` / On-Error Container: `#881337`

### Dark Mode Architecture
- **Background / Canvas:** `#0B1120` (Midnight Navy)
- **Card / Surface Container:** `#111827` (Rich Graphite)
- **Surface Container High:** `#1F2937`
- **Primary Dark:** `#2DD4BF`
- **On-Primary Dark:** `#042F2E`
- **Primary Container Dark:** `#134E4A`
- **Border Outline Dark:** `#1E293B`

## Typography

The type scale relies entirely on **Be Vietnam Pro**. Engineered with open apertures, generous x-height, and refined diacritical bounds, it eliminates mark clipping for Vietnamese tone marks (`hỏi`, `ngã`, `nặng`, `sắc`, `huyền`) and stacked vowel accents (`ê`, `ơ`, `ư`, `â`, `ă`).

- **Line Heights:** Set 10–15% taller than standard Western defaults to maintain optical rhythm when multi-tiered accents occur adjacent to ascending consonants.
- **Body Hierarchy:** `body-md` serves as the primary prose density for reading notes and AI explanations, preserving legibility at high information density.
- **Labels:** `label-lg` drives interactive touch surfaces, buttons, and navigation bar item labels.

## Layout & Spacing

Layout follows an adaptive **fluid grid** calibrated around handheld Android viewport economics:
- **Mobile (<600dp):** 4-column layout, 16px (`1rem`) margin, 16px (`1rem`) gutter. Content scales edge-to-edge with bottom navigation anchors.
- **Tablet / Foldables (600dp–840dp):** 8-column layout, 24px (`1.5rem`) margin, 24px (`1.5rem`) gutter. Persistent Navigation Rail replaces the bottom navigation bar.
- **Desktop / Landscape Large (>840dp):** 12-column layout, 32px (`2rem`) margin. Dual-pane view (e.g., chat thread on left, generative mind-map or flashcards on right).

All interactive elements adhere strictly to a **minimum touch target area of 48×48dp**, padded via internal safe targets when visual footprints are smaller.

## Elevation & Depth

Visual hierarchy combines **ambient diffused drop shadows** with **crisp low-contrast outlines** (`1px solid #E2E8F0` in Light Mode; `1px solid #1E293B` in Dark Mode) to prevent muddy layering on mobile screens.

- **Level 0 (Flat Canvas):** No shadow; relies on `#F8FAFC` (Dark: `#0B1120`).
- **Level 1 (Cards, Modules, Input Fields):**
  - Light: `0px 2px 4px -1px rgba(15, 23, 42, 0.04), 0px 4px 6px -1px rgba(15, 23, 42, 0.02)` bordered with `1px solid #E2E8F0`.
  - Dark: `0px 2px 4px -1px rgba(0, 0, 0, 0.35)` bordered with `1px solid #1E293B`.
- **Level 2 (Active Cards, Floating Controls, Menus):**
  - Light: `0px 6px 12px -2px rgba(15, 23, 42, 0.06), 0px 3px 6px -2px rgba(15, 23, 42, 0.03)`.
- **Level 3 (Bottom Navigation Bar, Top App Bar scrolled):**
  - Light: `0px -2px 10px rgba(15, 23, 42, 0.03)` with translucent surface tint backdrop blur (`backdrop-filter: blur(12px)`).
- **Level 4 (Modal Bottom Sheets, Floating AI Action Hub):**
  - Light: `0px 16px 32px -4px rgba(15, 23, 42, 0.12), 0px 8px 16px -4px rgba(15, 23, 42, 0.06)`.

## Shapes

The geometric vocabulary prioritizes organic, friendly contours without sacrificing functional density:
- **Default Core Radius:** 16px–20px (`rounded-lg` / `rounded-xl`).
- **Primary & Secondary Cards:** Standardized at `20px` corner radii.
- **Input Fields & Text Areas:** Styled with `16px` corner radii.
- **Dialogs & Bottom Sheets:** Bottom sheets employ `24px` top-corner radii; inner operational modules use `16px`.
- **Chips, Badges & Floating Buttons:** Fully pill-shaped (`9999px`) to immediately denote touchable utility.

## Components

### Buttons
- **Primary Action (Prominent):** Height `48px`, radius `16px` or full pill (`9999px`). Background `#0F766E`, text `#FFFFFF`, font `label-lg`.
- **Secondary / Tonal:** Height `48px`, radius `16px`. Background `#CCFBF1`, text `#115E59`.
- **Outlined:** Height `48px`, border `1px solid #E2E8F0`, surface transparent, text `#475569`.
- **Touch Target:** Guaranteed `min-height: 48px; min-width: 48px`.

### Navigation Architecture
- **Top App Bar:** Center-aligned or small format M3 bar. Surface background blends dynamically into `#F8FAFC`. Features clear title, contextual streak counter chip (`#FEF3C7` fill with `#F59E0B` icon and label), and user profile avatar.
- **Navigation Drawer:** Slides over content with `24px` right rounded edges. Contains academic track selector, subject tagging, study history, and settings.
- **Bottom Navigation Bar:** Height `80px`, background `#FFFFFF` with top border `1px solid #E2E8F0`. 
  - Houses 4 secondary destinations (Trang chủ, Lộ trình, Tài liệu, Cá nhân).
  - Features an **elevated prominent center action button** (StudyMind AI Quick Prompt/Scan) floating at `56×56px`, colored in Primary Teal `#0F766E` with elevated drop shadow, resting in an ergonomic thumb-reach dock.
- **Modal Bottom Sheets:** Top-left and top-right radii `24px`. Background `#FFFFFF` (Dark: `#111827`). Features a drag handle (`32×4px`, `#CBD5E1`) and handles contextual AI prompt actions, scan uploads, and quiz question breakdowns.

### Cards & Surfaces
- **Study Topic & Flashcard:** Border `1px solid #E2E8F0`, background `#FFFFFF`, radius `20px`, padding `16px` to `20px`.
- **AI Response Bubble:** Background `#F1F5F9` (User prompt) versus `#CCFBF1` (AI response preview). Code blocks and academic formulas sit inside nested containers with `12px` border radius and JetBrains Mono / MathJax formatting.

### Chips & Badges
- **Streak & StudyCoin Pill:** Background `#FEF3C7`, border `1px solid #FDE68A`, text `#78350F`, padding `4px 12px`, radius `9999px`.
- **Subject Filter Chips:** Height `36px`, min touch area `48px`. Active: `#0F766E` text with `#CCFBF1` background; Inactive: `#475569` text with `#F1F5F9` background.

### Input Fields
- **AI Prompt Box & Form Inputs:** Height `52px` (single-line) or dynamic auto-expand (multi-line). Radius `16px`, background `#FFFFFF`, border `1px solid #E2E8F0`. Focus state shifts border to `2px solid #0F766E`.

### Selection Controls
- **Checkboxes & Radios:** Scaled bounding box with minimum touch envelope of `48×48px`. Active fill `#0F766E`, inactive outline `#94A3B8`.
- **Quiz Option Tile:** Full-width card with selectable radio or checkbox integrated. When marked correct, transitions to `#DCFCE7` fill with `#16A34A` outline; when wrong, transitions to `#FFE4E6` fill with `#E11D48` outline.