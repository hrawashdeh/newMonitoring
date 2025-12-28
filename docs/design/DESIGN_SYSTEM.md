# Monitoring Platform Design System

**Document Purpose:** Complete UX/UI design system ensuring consistent experience across all pages with built-in customization
**Created:** 2025-12-26
**Technology:** React 18 + TypeScript + TailwindCSS + shadcn/ui
**Customization:** Theme-based configuration for logo, fonts, sizes, colors

---

## Table of Contents

1. [Design Principles](#design-principles)
2. [Design Tokens (Customizable)](#design-tokens-customizable)
3. [Typography System](#typography-system)
4. [Color System](#color-system)
5. [Spacing & Layout](#spacing--layout)
6. [Component Library](#component-library)
7. [Interaction Patterns](#interaction-patterns)
8. [Page Layouts](#page-layouts)
9. [Responsive Behavior](#responsive-behavior)
10. [Accessibility Standards](#accessibility-standards)
11. [Customization Guide](#customization-guide)

---

## Design Principles

### 1. Professional & Enterprise-Grade
- Clean, minimal interface without playful elements
- High information density for operational efficiency
- Conservative color palette (blues, grays, status colors)
- Bank-grade professionalism

### 2. Consistency
- Same components across all pages
- Predictable interaction patterns
- Unified visual language
- Reusable design patterns

### 3. Data-First
- Charts and visualizations prominent
- Quick stats/KPIs at top of pages
- Real-time updates where applicable
- Export capabilities on all data views

### 4. Action-Oriented
- Primary actions prominent and easy to find
- Destructive actions require confirmation
- Bulk operations where applicable
- Keyboard shortcuts for power users

### 5. Responsive & Accessible
- Mobile-first approach
- WCAG 2.1 Level AA compliance
- Keyboard navigation support
- Screen reader friendly

---

## Design Tokens (Customizable)

All design tokens are defined in a central configuration file for easy customization.

### Configuration File Structure

**File:** `frontend/src/config/theme.config.ts`

```typescript
export interface ThemeConfig {
  branding: BrandingTokens;
  typography: TypographyTokens;
  colors: ColorTokens;
  spacing: SpacingTokens;
  borders: BorderTokens;
  shadows: ShadowTokens;
  animation: AnimationTokens;
}

interface BrandingTokens {
  // Logo
  logo: {
    primary: string;           // Path to primary logo (dark mode)
    secondary?: string;         // Path to secondary logo (light mode)
    favicon: string;            // Path to favicon
    height: number;             // Logo height in pixels
    width?: number;             // Logo width (optional, auto if not set)
  };

  // Application name
  appName: string;              // e.g., "Monitoring Platform"
  appNameShort: string;         // e.g., "MonPlatform" (for mobile)

  // Tagline
  tagline?: string;             // Optional tagline under logo
}

interface TypographyTokens {
  // Font families (customizable)
  fontFamily: {
    sans: string[];             // Primary font stack
    mono: string[];             // Monospace for code
    heading?: string[];         // Optional separate heading font
  };

  // Font sizes (in rem, customizable)
  fontSize: {
    xs: string;                 // 0.75rem (12px)
    sm: string;                 // 0.875rem (14px)
    base: string;               // 1rem (16px)
    lg: string;                 // 1.125rem (18px)
    xl: string;                 // 1.25rem (20px)
    '2xl': string;              // 1.5rem (24px)
    '3xl': string;              // 1.875rem (30px)
    '4xl': string;              // 2.25rem (36px)
  };

  // Font weights
  fontWeight: {
    normal: number;             // 400
    medium: number;             // 500
    semibold: number;           // 600
    bold: number;               // 700
  };

  // Line heights
  lineHeight: {
    tight: number;              // 1.25
    normal: number;             // 1.5
    relaxed: number;            // 1.75
  };

  // Letter spacing
  letterSpacing: {
    tight: string;              // -0.025em
    normal: string;             // 0em
    wide: string;               // 0.025em
  };
}

interface ColorTokens {
  // Brand colors (customizable)
  brand: {
    primary: string;            // Main brand color (e.g., blue-600)
    primaryHover: string;       // Hover state
    primaryActive: string;      // Active state
    secondary?: string;         // Optional secondary brand color
  };

  // Semantic colors (customizable)
  semantic: {
    success: string;            // green-600
    successLight: string;       // green-100
    warning: string;            // yellow-600
    warningLight: string;       // yellow-100
    error: string;              // red-600
    errorLight: string;         // red-100
    info: string;               // blue-600
    infoLight: string;          // blue-100
  };

  // Status colors (for loader states, etc.)
  status: {
    active: string;             // green-600
    paused: string;             // gray-600
    failed: string;             // red-600
    running: string;            // blue-600
    pending: string;            // yellow-600
  };

  // Neutral colors (customizable)
  neutral: {
    50: string;                 // Lightest
    100: string;
    200: string;
    300: string;
    400: string;
    500: string;
    600: string;
    700: string;
    800: string;
    900: string;                // Darkest
    950: string;                // Extra dark
  };

  // UI colors (derived from neutral or custom)
  ui: {
    background: string;         // Page background
    foreground: string;         // Text color
    muted: string;              // Muted background
    mutedForeground: string;    // Muted text
    border: string;             // Border color
    input: string;              // Input border
    ring: string;               // Focus ring
    card: string;               // Card background
    cardForeground: string;     // Card text
  };
}

interface SpacingTokens {
  // Spacing scale (in rem, customizable)
  space: {
    0: string;                  // 0
    1: string;                  // 0.25rem (4px)
    2: string;                  // 0.5rem (8px)
    3: string;                  // 0.75rem (12px)
    4: string;                  // 1rem (16px)
    5: string;                  // 1.25rem (20px)
    6: string;                  // 1.5rem (24px)
    8: string;                  // 2rem (32px)
    10: string;                 // 2.5rem (40px)
    12: string;                 // 3rem (48px)
    16: string;                 // 4rem (64px)
    20: string;                 // 5rem (80px)
    24: string;                 // 6rem (96px)
  };

  // Container max widths
  container: {
    sm: string;                 // 640px
    md: string;                 // 768px
    lg: string;                 // 1024px
    xl: string;                 // 1280px
    '2xl': string;              // 1536px
  };
}

interface BorderTokens {
  // Border radius (customizable for brand feel)
  radius: {
    none: string;               // 0
    sm: string;                 // 0.125rem (2px)
    base: string;               // 0.25rem (4px)
    md: string;                 // 0.375rem (6px)
    lg: string;                 // 0.5rem (8px)
    xl: string;                 // 0.75rem (12px)
    '2xl': string;              // 1rem (16px)
    full: string;               // 9999px (pill shape)
  };

  // Border widths
  width: {
    0: string;                  // 0
    1: string;                  // 1px
    2: string;                  // 2px
    4: string;                  // 4px
    8: string;                  // 8px
  };
}

interface ShadowTokens {
  // Box shadows (customizable)
  shadow: {
    none: string;
    sm: string;                 // Small shadow
    base: string;               // Default shadow
    md: string;                 // Medium shadow
    lg: string;                 // Large shadow
    xl: string;                 // Extra large shadow
    '2xl': string;              // 2x large shadow
  };
}

interface AnimationTokens {
  // Transition durations (customizable)
  duration: {
    fast: string;               // 150ms
    base: string;               // 200ms
    slow: string;               // 300ms
    slower: string;             // 500ms
  };

  // Transition timing functions
  timing: {
    linear: string;
    easeIn: string;
    easeOut: string;
    easeInOut: string;
  };
}
```

### Default Theme Configuration

**File:** `frontend/src/config/defaultTheme.ts`

```typescript
import { ThemeConfig } from './theme.config';

export const defaultTheme: ThemeConfig = {
  branding: {
    logo: {
      primary: '/assets/logo/logo-primary.svg',
      secondary: '/assets/logo/logo-light.svg',
      favicon: '/assets/logo/favicon.ico',
      height: 32,
    },
    appName: 'Monitoring Platform',
    appNameShort: 'MonPlatform',
    tagline: 'Enterprise Data Monitoring',
  },

  typography: {
    fontFamily: {
      sans: ['Inter', 'system-ui', 'sans-serif'],
      mono: ['JetBrains Mono', 'Courier New', 'monospace'],
      heading: ['Inter', 'system-ui', 'sans-serif'],
    },
    fontSize: {
      xs: '0.75rem',      // 12px
      sm: '0.875rem',     // 14px
      base: '1rem',       // 16px
      lg: '1.125rem',     // 18px
      xl: '1.25rem',      // 20px
      '2xl': '1.5rem',    // 24px
      '3xl': '1.875rem',  // 30px
      '4xl': '2.25rem',   // 36px
    },
    fontWeight: {
      normal: 400,
      medium: 500,
      semibold: 600,
      bold: 700,
    },
    lineHeight: {
      tight: 1.25,
      normal: 1.5,
      relaxed: 1.75,
    },
    letterSpacing: {
      tight: '-0.025em',
      normal: '0em',
      wide: '0.025em',
    },
  },

  colors: {
    brand: {
      primary: 'hsl(222, 47%, 41%)',        // Blue-600
      primaryHover: 'hsl(222, 47%, 36%)',   // Blue-700
      primaryActive: 'hsl(222, 47%, 31%)',  // Blue-800
      secondary: 'hsl(215, 28%, 17%)',      // Slate-900
    },
    semantic: {
      success: 'hsl(142, 71%, 45%)',        // Green-600
      successLight: 'hsl(142, 76%, 96%)',   // Green-100
      warning: 'hsl(38, 92%, 50%)',         // Yellow-600
      warningLight: 'hsl(48, 96%, 89%)',    // Yellow-100
      error: 'hsl(0, 72%, 51%)',            // Red-600
      errorLight: 'hsl(0, 86%, 97%)',       // Red-100
      info: 'hsl(199, 89%, 48%)',           // Blue-600
      infoLight: 'hsl(204, 94%, 94%)',      // Blue-100
    },
    status: {
      active: 'hsl(142, 71%, 45%)',         // Green-600
      paused: 'hsl(215, 16%, 47%)',         // Gray-600
      failed: 'hsl(0, 72%, 51%)',           // Red-600
      running: 'hsl(199, 89%, 48%)',        // Blue-600
      pending: 'hsl(38, 92%, 50%)',         // Yellow-600
    },
    neutral: {
      50: 'hsl(210, 40%, 98%)',
      100: 'hsl(210, 40%, 96%)',
      200: 'hsl(214, 32%, 91%)',
      300: 'hsl(213, 27%, 84%)',
      400: 'hsl(215, 20%, 65%)',
      500: 'hsl(215, 16%, 47%)',
      600: 'hsl(215, 19%, 35%)',
      700: 'hsl(215, 25%, 27%)',
      800: 'hsl(217, 33%, 17%)',
      900: 'hsl(222, 47%, 11%)',
      950: 'hsl(229, 84%, 5%)',
    },
    ui: {
      background: 'hsl(0, 0%, 100%)',
      foreground: 'hsl(222, 47%, 11%)',
      muted: 'hsl(210, 40%, 96%)',
      mutedForeground: 'hsl(215, 16%, 47%)',
      border: 'hsl(214, 32%, 91%)',
      input: 'hsl(214, 32%, 91%)',
      ring: 'hsl(222, 47%, 41%)',
      card: 'hsl(0, 0%, 100%)',
      cardForeground: 'hsl(222, 47%, 11%)',
    },
  },

  spacing: {
    space: {
      0: '0',
      1: '0.25rem',
      2: '0.5rem',
      3: '0.75rem',
      4: '1rem',
      5: '1.25rem',
      6: '1.5rem',
      8: '2rem',
      10: '2.5rem',
      12: '3rem',
      16: '4rem',
      20: '5rem',
      24: '6rem',
    },
    container: {
      sm: '640px',
      md: '768px',
      lg: '1024px',
      xl: '1280px',
      '2xl': '1536px',
    },
  },

  borders: {
    radius: {
      none: '0',
      sm: '0.125rem',
      base: '0.25rem',
      md: '0.375rem',
      lg: '0.5rem',
      xl: '0.75rem',
      '2xl': '1rem',
      full: '9999px',
    },
    width: {
      0: '0',
      1: '1px',
      2: '2px',
      4: '4px',
      8: '8px',
    },
  },

  shadows: {
    shadow: {
      none: 'none',
      sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
      base: '0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)',
      md: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)',
      lg: '0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)',
      xl: '0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1)',
      '2xl': '0 25px 50px -12px rgb(0 0 0 / 0.25)',
    },
  },

  animation: {
    duration: {
      fast: '150ms',
      base: '200ms',
      slow: '300ms',
      slower: '500ms',
    },
    timing: {
      linear: 'linear',
      easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
      easeOut: 'cubic-bezier(0, 0, 0.2, 1)',
      easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
    },
  },
};
```

### Theme Provider

**File:** `frontend/src/contexts/ThemeContext.tsx`

```typescript
import React, { createContext, useContext, useMemo } from 'react';
import { ThemeConfig } from '@/config/theme.config';
import { defaultTheme } from '@/config/defaultTheme';

interface ThemeContextType {
  theme: ThemeConfig;
}

const ThemeContext = createContext<ThemeContextType>({ theme: defaultTheme });

export const useTheme = () => useContext(ThemeContext);

interface ThemeProviderProps {
  children: React.ReactNode;
  customTheme?: Partial<ThemeConfig>;
}

export const ThemeProvider: React.FC<ThemeProviderProps> = ({ children, customTheme }) => {
  // Merge custom theme with default theme
  const theme = useMemo(() => {
    if (!customTheme) return defaultTheme;

    return {
      branding: { ...defaultTheme.branding, ...customTheme.branding },
      typography: { ...defaultTheme.typography, ...customTheme.typography },
      colors: { ...defaultTheme.colors, ...customTheme.colors },
      spacing: { ...defaultTheme.spacing, ...customTheme.spacing },
      borders: { ...defaultTheme.borders, ...customTheme.borders },
      shadows: { ...defaultTheme.shadows, ...customTheme.shadows },
      animation: { ...defaultTheme.animation, ...customTheme.animation },
    };
  }, [customTheme]);

  // Inject CSS variables for theme
  React.useEffect(() => {
    const root = document.documentElement;

    // Typography
    root.style.setProperty('--font-sans', theme.typography.fontFamily.sans.join(', '));
    root.style.setProperty('--font-mono', theme.typography.fontFamily.mono.join(', '));

    // Colors (example - do this for all color tokens)
    root.style.setProperty('--color-primary', theme.colors.brand.primary);
    root.style.setProperty('--color-success', theme.colors.semantic.success);
    // ... set all other colors

    // Spacing, borders, shadows, etc.
    // ... set all other tokens as CSS variables
  }, [theme]);

  return (
    <ThemeContext.Provider value={{ theme }}>
      {children}
    </ThemeContext.Provider>
  );
};
```

---

## Typography System

### Hierarchy

```typescript
// Typography scale with consistent usage
const typographyScale = {
  // Display (hero text, landing pages)
  display: {
    fontSize: 'text-4xl',      // 36px
    fontWeight: 'font-bold',
    lineHeight: 'leading-tight',
    letterSpacing: 'tracking-tight',
  },

  // Page titles
  h1: {
    fontSize: 'text-3xl',      // 30px
    fontWeight: 'font-bold',
    lineHeight: 'leading-tight',
    letterSpacing: 'tracking-tight',
  },

  // Section titles
  h2: {
    fontSize: 'text-2xl',      // 24px
    fontWeight: 'font-semibold',
    lineHeight: 'leading-normal',
  },

  // Subsection titles
  h3: {
    fontSize: 'text-xl',       // 20px
    fontWeight: 'font-semibold',
    lineHeight: 'leading-normal',
  },

  // Card titles
  h4: {
    fontSize: 'text-lg',       // 18px
    fontWeight: 'font-medium',
    lineHeight: 'leading-normal',
  },

  // Body large (emphasis)
  bodyLarge: {
    fontSize: 'text-base',     // 16px
    fontWeight: 'font-normal',
    lineHeight: 'leading-relaxed',
  },

  // Body (default)
  body: {
    fontSize: 'text-sm',       // 14px
    fontWeight: 'font-normal',
    lineHeight: 'leading-normal',
  },

  // Body small (secondary info)
  bodySmall: {
    fontSize: 'text-xs',       // 12px
    fontWeight: 'font-normal',
    lineHeight: 'leading-normal',
  },

  // Label (form labels, captions)
  label: {
    fontSize: 'text-sm',       // 14px
    fontWeight: 'font-medium',
    lineHeight: 'leading-normal',
  },

  // Caption (muted text, timestamps)
  caption: {
    fontSize: 'text-xs',       // 12px
    fontWeight: 'font-normal',
    lineHeight: 'leading-normal',
    color: 'text-muted-foreground',
  },

  // Code (monospace)
  code: {
    fontSize: 'text-sm',       // 14px
    fontFamily: 'font-mono',
    fontWeight: 'font-normal',
  },
};
```

### Typography Component

**File:** `frontend/src/components/ui/Typography.tsx`

```typescript
import React from 'react';
import { cn } from '@/lib/utils';

type TypographyVariant =
  | 'display'
  | 'h1'
  | 'h2'
  | 'h3'
  | 'h4'
  | 'bodyLarge'
  | 'body'
  | 'bodySmall'
  | 'label'
  | 'caption'
  | 'code';

interface TypographyProps {
  variant: TypographyVariant;
  children: React.ReactNode;
  className?: string;
  as?: 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'p' | 'span' | 'div' | 'code';
}

const variantStyles: Record<TypographyVariant, string> = {
  display: 'text-4xl font-bold leading-tight tracking-tight',
  h1: 'text-3xl font-bold leading-tight tracking-tight',
  h2: 'text-2xl font-semibold leading-normal',
  h3: 'text-xl font-semibold leading-normal',
  h4: 'text-lg font-medium leading-normal',
  bodyLarge: 'text-base font-normal leading-relaxed',
  body: 'text-sm font-normal leading-normal',
  bodySmall: 'text-xs font-normal leading-normal',
  label: 'text-sm font-medium leading-normal',
  caption: 'text-xs font-normal leading-normal text-muted-foreground',
  code: 'text-sm font-mono font-normal',
};

const defaultElements: Record<TypographyVariant, string> = {
  display: 'h1',
  h1: 'h1',
  h2: 'h2',
  h3: 'h3',
  h4: 'h4',
  bodyLarge: 'p',
  body: 'p',
  bodySmall: 'p',
  label: 'label',
  caption: 'span',
  code: 'code',
};

export const Typography: React.FC<TypographyProps> = ({
  variant,
  children,
  className,
  as
}) => {
  const Component = as || defaultElements[variant];

  return (
    <Component className={cn(variantStyles[variant], className)}>
      {children}
    </Component>
  );
};

// Convenience components
export const Heading1 = (props: Omit<TypographyProps, 'variant'>) => (
  <Typography variant="h1" {...props} />
);

export const Heading2 = (props: Omit<TypographyProps, 'variant'>) => (
  <Typography variant="h2" {...props} />
);

export const Heading3 = (props: Omit<TypographyProps, 'variant'>) => (
  <Typography variant="h3" {...props} />
);

export const Heading4 = (props: Omit<TypographyProps, 'variant'>) => (
  <Typography variant="h4" {...props} />
);

export const Body = (props: Omit<TypographyProps, 'variant'>) => (
  <Typography variant="body" {...props} />
);

export const Caption = (props: Omit<TypographyProps, 'variant'>) => (
  <Typography variant="caption" {...props} />
);
```

---

## Color System

### Color Usage Guidelines

```typescript
// Color usage mapping
const colorUsage = {
  // Text colors
  text: {
    primary: 'text-foreground',           // Main text (neutral-900)
    secondary: 'text-muted-foreground',   // Secondary text (neutral-600)
    disabled: 'text-neutral-400',         // Disabled text
    inverse: 'text-white',                // Text on dark backgrounds
    link: 'text-primary',                 // Links
    linkHover: 'text-primary/80',         // Link hover
  },

  // Background colors
  background: {
    page: 'bg-background',                // Page background (white)
    card: 'bg-card',                      // Card background (white)
    muted: 'bg-muted',                    // Muted background (neutral-100)
    accent: 'bg-accent',                  // Accent background
    hover: 'hover:bg-muted',              // Hover state
    selected: 'bg-primary/10',            // Selected state
  },

  // Border colors
  border: {
    default: 'border-border',             // Default border (neutral-200)
    muted: 'border-neutral-100',          // Subtle border
    strong: 'border-neutral-300',         // Strong border
    focus: 'focus:ring-2 focus:ring-ring',// Focus ring
  },

  // Status colors
  status: {
    success: 'bg-green-600 text-white',
    warning: 'bg-yellow-600 text-white',
    error: 'bg-red-600 text-white',
    info: 'bg-blue-600 text-white',

    successLight: 'bg-green-100 text-green-800',
    warningLight: 'bg-yellow-100 text-yellow-800',
    errorLight: 'bg-red-100 text-red-800',
    infoLight: 'bg-blue-100 text-blue-800',
  },

  // Loader status badges
  loaderStatus: {
    ACTIVE: 'bg-green-600 text-white',
    PAUSED: 'bg-gray-600 text-white',
    FAILED: 'bg-red-600 text-white',
    RUNNING: 'bg-blue-600 text-white',
    PENDING: 'bg-yellow-600 text-white',
    SUCCESS: 'bg-green-600 text-white',
  },
};
```

### Status Badge Component

**File:** `frontend/src/components/ui/StatusBadge.tsx`

```typescript
import React from 'react';
import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';

type LoaderStatus = 'ACTIVE' | 'PAUSED' | 'FAILED';
type JobStatus = 'RUNNING' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';

interface StatusBadgeProps {
  status: LoaderStatus | JobStatus;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const statusConfig = {
  ACTIVE: {
    label: 'Active',
    icon: '✅',
    className: 'bg-green-600 text-white hover:bg-green-700',
  },
  PAUSED: {
    label: 'Paused',
    icon: '⏸️',
    className: 'bg-gray-600 text-white hover:bg-gray-700',
  },
  FAILED: {
    label: 'Failed',
    icon: '❌',
    className: 'bg-red-600 text-white hover:bg-red-700',
  },
  RUNNING: {
    label: 'Running',
    icon: '🔵',
    className: 'bg-blue-600 text-white hover:bg-blue-700',
  },
  PENDING: {
    label: 'Pending',
    icon: '⏳',
    className: 'bg-yellow-600 text-white hover:bg-yellow-700',
  },
  SUCCESS: {
    label: 'Success',
    icon: '✅',
    className: 'bg-green-600 text-white hover:bg-green-700',
  },
  CANCELLED: {
    label: 'Cancelled',
    icon: '⚪',
    className: 'bg-gray-500 text-white hover:bg-gray-600',
  },
};

const sizeClasses = {
  sm: 'text-xs px-2 py-0.5',
  md: 'text-sm px-2.5 py-1',
  lg: 'text-base px-3 py-1.5',
};

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  size = 'md',
  className
}) => {
  const config = statusConfig[status];

  return (
    <Badge
      variant="default"
      className={cn(
        config.className,
        sizeClasses[size],
        'font-medium',
        className
      )}
    >
      <span className="mr-1">{config.icon}</span>
      {config.label}
    </Badge>
  );
};
```

---

## Spacing & Layout

### Spacing Scale

```typescript
// Consistent spacing usage
const spacing = {
  // Component internal spacing
  componentPadding: {
    sm: 'p-2',      // 8px (compact components)
    md: 'p-4',      // 16px (default)
    lg: 'p-6',      // 24px (spacious components)
  },

  // Gap between elements
  gap: {
    xs: 'gap-1',    // 4px (tight)
    sm: 'gap-2',    // 8px (compact)
    md: 'gap-4',    // 16px (default)
    lg: 'gap-6',    // 24px (spacious)
    xl: 'gap-8',    // 32px (very spacious)
  },

  // Section spacing
  section: {
    sm: 'space-y-4',   // 16px between sections
    md: 'space-y-6',   // 24px
    lg: 'space-y-8',   // 32px
  },

  // Page margins
  page: {
    x: 'px-4 sm:px-6 lg:px-8',     // Horizontal padding (responsive)
    y: 'py-6 sm:py-8 lg:py-12',    // Vertical padding
  },
};
```

### Layout Grid

```typescript
// Responsive grid patterns
const gridPatterns = {
  // Stats cards (1-2-4 columns)
  stats: 'grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4',

  // Action cards (1-2-3 columns)
  cards: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6',

  // Form layout (1-2 columns)
  form: 'grid grid-cols-1 md:grid-cols-2 gap-4',

  // Sidebar layout
  sidebar: 'grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-6',

  // Two column content
  twoColumn: 'grid grid-cols-1 lg:grid-cols-2 gap-8',
};
```

### Container Component

**File:** `frontend/src/components/ui/Container.tsx`

```typescript
import React from 'react';
import { cn } from '@/lib/utils';

interface ContainerProps {
  children: React.ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full';
  className?: string;
}

const sizeClasses = {
  sm: 'max-w-screen-sm',      // 640px
  md: 'max-w-screen-md',      // 768px
  lg: 'max-w-screen-lg',      // 1024px
  xl: 'max-w-screen-xl',      // 1280px
  '2xl': 'max-w-screen-2xl',  // 1536px
  full: 'max-w-full',
};

export const Container: React.FC<ContainerProps> = ({
  children,
  size = 'xl',
  className
}) => {
  return (
    <div className={cn(
      sizeClasses[size],
      'mx-auto px-4 sm:px-6 lg:px-8',
      className
    )}>
      {children}
    </div>
  );
};
```

---

## Component Library

### Core Components (shadcn/ui based)

All components follow consistent patterns and are customizable via theme tokens.

#### 1. Button

**File:** `frontend/src/components/ui/Button.tsx` (extends shadcn/ui)

```typescript
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const buttonVariants = cva(
  // Base styles (consistent across all variants)
  'inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none',
  {
    variants: {
      variant: {
        default: 'bg-primary text-white hover:bg-primary/90',
        destructive: 'bg-red-600 text-white hover:bg-red-700',
        outline: 'border border-input hover:bg-muted',
        secondary: 'bg-muted text-foreground hover:bg-muted/80',
        ghost: 'hover:bg-muted hover:text-foreground',
        link: 'underline-offset-4 hover:underline text-primary',
      },
      size: {
        sm: 'h-8 px-3 text-xs',
        md: 'h-10 px-4 py-2',
        lg: 'h-11 px-8 text-base',
        icon: 'h-10 w-10',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'md',
    },
  }
);

// Usage in pages
<Button variant="default" size="md">Create Loader</Button>
<Button variant="destructive" size="sm">Delete</Button>
<Button variant="outline">Cancel</Button>
```

#### 2. Card

```typescript
// Card component with consistent styling
<Card className="hover:shadow-lg transition-shadow">
  <CardHeader>
    <CardTitle>Loader Statistics</CardTitle>
    <CardDescription>Operational metrics</CardDescription>
  </CardHeader>
  <CardContent>
    {/* Content */}
  </CardContent>
  <CardFooter>
    {/* Footer actions */}
  </CardFooter>
</Card>
```

#### 3. Input

```typescript
// Input with consistent styling
<Input
  type="text"
  placeholder="Search loaders..."
  className="max-w-md"
/>

// With label
<div className="space-y-2">
  <Label htmlFor="loaderCode">Loader Code</Label>
  <Input
    id="loaderCode"
    placeholder="DAILY_SALES"
    required
  />
</div>
```

#### 4. Table (TanStack Table wrapper)

```typescript
// Consistent table styling
<DataTable
  columns={columns}
  data={data}
  className="border rounded-lg"
/>

// Table with all standard features
<DataTable
  columns={columns}
  data={data}
  searchable          // Add search input
  filterable          // Add filter dropdowns
  pagination          // Add pagination
  pageSize={10}       // Default page size
  onRowClick={handleRowClick}
/>
```

### Custom Components

#### StatsCard

**File:** `frontend/src/components/common/StatsCard.tsx`

```typescript
import React from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import { ArrowUp, ArrowDown } from 'lucide-react';

interface StatsCardProps {
  label: string;
  value: number | string;
  subtitle?: string;
  icon?: React.ReactNode;
  trend?: {
    direction: 'up' | 'down' | 'neutral';
    value: string;
  };
  status?: 'success' | 'warning' | 'error' | 'neutral';
  className?: string;
}

const statusColors = {
  success: 'text-green-600 bg-green-50',
  warning: 'text-yellow-600 bg-yellow-50',
  error: 'text-red-600 bg-red-50',
  neutral: 'text-gray-600 bg-gray-50',
};

export const StatsCard: React.FC<StatsCardProps> = ({
  label,
  value,
  subtitle,
  icon,
  trend,
  status = 'neutral',
  className,
}) => {
  return (
    <Card className={cn('hover:shadow-md transition-shadow', className)}>
      <CardContent className="p-6">
        <div className="flex items-start justify-between">
          <div className="flex-1">
            <p className="text-sm font-medium text-muted-foreground">{label}</p>
            <h3 className="text-3xl font-bold mt-2">{value}</h3>
            {subtitle && (
              <p className="text-sm text-muted-foreground mt-1">{subtitle}</p>
            )}
            {trend && (
              <div className="flex items-center mt-2 text-sm">
                {trend.direction === 'up' && (
                  <ArrowUp className="h-4 w-4 text-green-600 mr-1" />
                )}
                {trend.direction === 'down' && (
                  <ArrowDown className="h-4 w-4 text-red-600 mr-1" />
                )}
                <span className={cn(
                  trend.direction === 'up' && 'text-green-600',
                  trend.direction === 'down' && 'text-red-600',
                  trend.direction === 'neutral' && 'text-gray-600'
                )}>
                  {trend.value}
                </span>
              </div>
            )}
          </div>
          {icon && (
            <div className={cn(
              'p-3 rounded-lg',
              statusColors[status]
            )}>
              {icon}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
};

// Usage
<StatsCard
  label="Active Loaders"
  value={24}
  subtitle="86% of total"
  icon={<CheckCircle className="h-6 w-6" />}
  trend={{ direction: 'up', value: '+2 (24h)' }}
  status="success"
/>
```

#### PageHeader

**File:** `frontend/src/components/common/PageHeader.tsx`

```typescript
import React from 'react';
import { cn } from '@/lib/utils';
import { Typography } from '@/components/ui/Typography';

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: React.ReactNode;
  breadcrumbs?: React.ReactNode;
  className?: string;
}

export const PageHeader: React.FC<PageHeaderProps> = ({
  title,
  description,
  actions,
  breadcrumbs,
  className,
}) => {
  return (
    <div className={cn('border-b bg-white', className)}>
      <div className="px-4 sm:px-6 lg:px-8 py-6">
        {breadcrumbs && <div className="mb-4">{breadcrumbs}</div>}

        <div className="flex items-center justify-between">
          <div className="flex-1">
            <Typography variant="h1">{title}</Typography>
            {description && (
              <Typography variant="body" className="mt-2 text-muted-foreground">
                {description}
              </Typography>
            )}
          </div>

          {actions && (
            <div className="flex items-center gap-3">
              {actions}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

// Usage
<PageHeader
  title="Loaders Overview"
  description="Manage ETL loaders and data sources"
  breadcrumbs={<Breadcrumbs items={['Home', 'Loaders']} />}
  actions={
    <>
      <Button variant="outline">Refresh</Button>
      <Button>Create Loader</Button>
    </>
  }
/>
```

---

## Interaction Patterns

### 1. Loading States

```typescript
// Consistent loading indicators
import { Skeleton } from '@/components/ui/skeleton';

// Loading table
<div className="space-y-2">
  <Skeleton className="h-10 w-full" />
  <Skeleton className="h-10 w-full" />
  <Skeleton className="h-10 w-full" />
</div>

// Loading card
<Card>
  <CardHeader>
    <Skeleton className="h-6 w-40" />
    <Skeleton className="h-4 w-60 mt-2" />
  </CardHeader>
  <CardContent>
    <Skeleton className="h-24 w-full" />
  </CardContent>
</Card>

// Loading stats
<div className="grid grid-cols-4 gap-4">
  {[...Array(4)].map((_, i) => (
    <Card key={i}>
      <CardContent className="p-6">
        <Skeleton className="h-4 w-20" />
        <Skeleton className="h-8 w-16 mt-2" />
      </CardContent>
    </Card>
  ))}
</div>
```

### 2. Error States

```typescript
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { AlertCircle } from 'lucide-react';

// Error alert
<Alert variant="destructive">
  <AlertCircle className="h-4 w-4" />
  <AlertTitle>Error loading loaders</AlertTitle>
  <AlertDescription>
    Failed to fetch loader data. Please try again.
  </AlertDescription>
</Alert>

// Empty state
<div className="text-center py-12">
  <Database className="h-12 w-12 mx-auto text-muted-foreground" />
  <Typography variant="h3" className="mt-4">No loaders found</Typography>
  <Typography variant="body" className="mt-2 text-muted-foreground">
    Get started by creating your first loader
  </Typography>
  <Button className="mt-6">Create Loader</Button>
</div>
```

### 3. Toast Notifications

```typescript
import { toast } from 'sonner';

// Success notification
toast.success('Loader created successfully', {
  description: 'DAILY_SALES is now active',
  duration: 3000,
});

// Error notification
toast.error('Failed to delete loader', {
  description: 'The loader is being used by active jobs',
  duration: 5000,
});

// Loading notification (with promise)
toast.promise(
  deleteLoader(loaderCode),
  {
    loading: 'Deleting loader...',
    success: 'Loader deleted successfully',
    error: 'Failed to delete loader',
  }
);
```

### 4. Confirmation Dialogs

```typescript
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

// Delete confirmation
<AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
  <AlertDialogContent>
    <AlertDialogHeader>
      <AlertDialogTitle>Delete Loader</AlertDialogTitle>
      <AlertDialogDescription>
        Are you sure you want to delete <strong>{loaderCode}</strong>?
        This action cannot be undone.
      </AlertDialogDescription>
    </AlertDialogHeader>
    <AlertDialogFooter>
      <AlertDialogCancel>Cancel</AlertDialogCancel>
      <AlertDialogAction
        onClick={handleDelete}
        className="bg-red-600 hover:bg-red-700"
      >
        Delete
      </AlertDialogAction>
    </AlertDialogFooter>
  </AlertDialogContent>
</AlertDialog>
```

### 5. Form Validation

```typescript
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';

const loaderSchema = z.object({
  loaderCode: z.string()
    .min(1, 'Loader code is required')
    .max(64, 'Maximum 64 characters')
    .regex(/^[A-Z0-9_]+$/, 'Only uppercase, numbers, and underscores'),
  sourceCode: z.string().min(1, 'Source database is required'),
});

// Form with inline validation
<Form {...form}>
  <form onSubmit={form.handleSubmit(onSubmit)}>
    <FormField
      control={form.control}
      name="loaderCode"
      render={({ field }) => (
        <FormItem>
          <FormLabel>Loader Code</FormLabel>
          <FormControl>
            <Input placeholder="DAILY_SALES" {...field} />
          </FormControl>
          <FormDescription>
            Alphanumeric + underscore, max 64 chars
          </FormDescription>
          <FormMessage /> {/* Shows error messages */}
        </FormItem>
      )}
    />
  </form>
</Form>
```

---

## Page Layouts

### Standard Page Template

**File:** `frontend/src/components/layouts/StandardPage.tsx`

```typescript
import React from 'react';
import { Container } from '@/components/ui/Container';
import { PageHeader } from '@/components/common/PageHeader';

interface StandardPageProps {
  title: string;
  description?: string;
  actions?: React.ReactNode;
  breadcrumbs?: React.ReactNode;
  children: React.ReactNode;
  containerSize?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full';
}

export const StandardPage: React.FC<StandardPageProps> = ({
  title,
  description,
  actions,
  breadcrumbs,
  children,
  containerSize = 'xl',
}) => {
  return (
    <div className="min-h-screen bg-background">
      <PageHeader
        title={title}
        description={description}
        actions={actions}
        breadcrumbs={breadcrumbs}
      />

      <main className="py-6 sm:py-8">
        <Container size={containerSize}>
          {children}
        </Container>
      </main>
    </div>
  );
};

// Usage
<StandardPage
  title="Loaders Overview"
  description="Manage ETL loaders"
  actions={<Button>Create Loader</Button>}
>
  {/* Page content */}
</StandardPage>
```

### App Shell Layout

**File:** `frontend/src/components/layouts/AppShell.tsx`

```typescript
import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { cn } from '@/lib/utils';

export const AppShell: React.FC = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="min-h-screen bg-background">
      {/* Desktop Sidebar */}
      <aside className="hidden lg:fixed lg:inset-y-0 lg:flex lg:w-64 lg:flex-col">
        <Sidebar />
      </aside>

      {/* Mobile Sidebar */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="fixed inset-0 bg-black/50"
            onClick={() => setSidebarOpen(false)}
          />
          <aside className="fixed inset-y-0 left-0 w-64 bg-white">
            <Sidebar onClose={() => setSidebarOpen(false)} />
          </aside>
        </div>
      )}

      {/* Main Content */}
      <div className="lg:pl-64">
        <Header onMenuClick={() => setSidebarOpen(true)} />
        <main>
          <Outlet />
        </main>
      </div>
    </div>
  );
};
```

---

## Responsive Behavior

### Breakpoints

```typescript
// Tailwind breakpoints (consistent across all pages)
const breakpoints = {
  sm: '640px',   // Mobile landscape
  md: '768px',   // Tablet
  lg: '1024px',  // Desktop
  xl: '1280px',  // Large desktop
  '2xl': '1536px', // Extra large
};

// Usage in components
<div className="
  grid
  grid-cols-1          {/* Mobile: 1 column */}
  md:grid-cols-2       {/* Tablet: 2 columns */}
  lg:grid-cols-3       {/* Desktop: 3 columns */}
  xl:grid-cols-4       {/* Large: 4 columns */}
  gap-4
">
```

### Responsive Patterns

#### Hide/Show Elements

```typescript
// Show only on desktop
<div className="hidden lg:block">Desktop content</div>

// Show only on mobile
<div className="lg:hidden">Mobile content</div>

// Different layouts per breakpoint
<div className="
  flex flex-col        {/* Mobile: vertical stack */}
  md:flex-row          {/* Tablet+: horizontal */}
  gap-4
">
```

#### Responsive Typography

```typescript
<h1 className="
  text-2xl             {/* Mobile: 24px */}
  md:text-3xl          {/* Tablet: 30px */}
  lg:text-4xl          {/* Desktop: 36px */}
  font-bold
">
  Page Title
</h1>
```

#### Responsive Spacing

```typescript
<div className="
  p-4                  {/* Mobile: 16px padding */}
  md:p-6               {/* Tablet: 24px */}
  lg:p-8               {/* Desktop: 32px */}
">
```

---

## Accessibility Standards

### WCAG 2.1 Level AA Compliance

#### 1. Keyboard Navigation

```typescript
// Focus visible
<button className="
  focus:outline-none
  focus:ring-2
  focus:ring-ring
  focus:ring-offset-2
">

// Tab order (logical)
<div>
  <button tabIndex={1}>First</button>
  <button tabIndex={2}>Second</button>
  <button tabIndex={3}>Third</button>
</div>

// Skip to main content
<a
  href="#main-content"
  className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4"
>
  Skip to main content
</a>
```

#### 2. ARIA Labels

```typescript
// Icon-only buttons
<button aria-label="Delete loader">
  <Trash className="h-4 w-4" />
</button>

// Form labels
<Label htmlFor="loaderCode">Loader Code</Label>
<Input id="loaderCode" aria-describedby="loaderCode-description" />
<p id="loaderCode-description" className="text-sm text-muted-foreground">
  Alphanumeric + underscore only
</p>

// Live regions
<div aria-live="polite" aria-atomic="true">
  {successMessage}
</div>
```

#### 3. Color Contrast

```typescript
// Minimum 4.5:1 for normal text
// Minimum 3:1 for large text (18px+ or 14px+ bold)

// Status indicators (never color alone)
<span className="flex items-center gap-2">
  <CheckCircle className="h-4 w-4 text-green-600" />
  <span>Success</span>
</span>
```

#### 4. Screen Readers

```typescript
// Hide decorative elements
<div aria-hidden="true">
  {/* Decorative icon */}
</div>

// Descriptive text for screen readers
<span className="sr-only">Loading loaders...</span>
<Spinner aria-hidden="true" />
```

---

## Customization Guide

### How to Customize the Theme

#### 1. Create Custom Theme File

**File:** `frontend/src/config/customTheme.ts`

```typescript
import { ThemeConfig } from './theme.config';

export const customTheme: Partial<ThemeConfig> = {
  branding: {
    logo: {
      primary: '/assets/logo/acme-logo.svg',
      favicon: '/assets/logo/acme-favicon.ico',
      height: 40,
    },
    appName: 'ACME Monitoring',
    appNameShort: 'ACME',
  },

  typography: {
    fontFamily: {
      sans: ['Poppins', 'system-ui', 'sans-serif'],
      heading: ['Poppins', 'system-ui', 'sans-serif'],
    },
    fontSize: {
      base: '1.125rem',  // Larger base font (18px instead of 16px)
    },
  },

  colors: {
    brand: {
      primary: 'hsl(271, 91%, 65%)',    // Purple brand color
      primaryHover: 'hsl(271, 91%, 60%)',
      primaryActive: 'hsl(271, 91%, 55%)',
    },
  },

  borders: {
    radius: {
      base: '0.5rem',    // More rounded corners (8px instead of 4px)
      lg: '1rem',        // 16px
    },
  },
};
```

#### 2. Apply Custom Theme

**File:** `frontend/src/App.tsx`

```typescript
import { ThemeProvider } from '@/contexts/ThemeContext';
import { customTheme } from '@/config/customTheme';

function App() {
  return (
    <ThemeProvider customTheme={customTheme}>
      {/* App content */}
    </ThemeProvider>
  );
}
```

#### 3. Update Tailwind Config

**File:** `tailwind.config.ts`

```typescript
import type { Config } from 'tailwindcss';
import { customTheme } from './src/config/customTheme';

const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Inject custom colors
        primary: customTheme.colors?.brand?.primary || defaultTheme.colors.brand.primary,
        // ... other colors
      },
      fontFamily: {
        sans: customTheme.typography?.fontFamily?.sans || defaultTheme.typography.fontFamily.sans,
        mono: customTheme.typography?.fontFamily?.mono || defaultTheme.typography.fontFamily.mono,
      },
      // ... other customizations
    },
  },
  plugins: [require('tailwindcss-animate')],
};

export default config;
```

### Customization Examples

#### Example 1: Corporate Blue Theme

```typescript
export const corporateBlueTheme: Partial<ThemeConfig> = {
  colors: {
    brand: {
      primary: 'hsl(210, 100%, 40%)',
      primaryHover: 'hsl(210, 100%, 35%)',
      primaryActive: 'hsl(210, 100%, 30%)',
    },
  },
};
```

#### Example 2: Dark Brand Theme

```typescript
export const darkBrandTheme: Partial<ThemeConfig> = {
  colors: {
    brand: {
      primary: 'hsl(0, 0%, 10%)',
      primaryHover: 'hsl(0, 0%, 15%)',
      primaryActive: 'hsl(0, 0%, 20%)',
    },
  },
};
```

#### Example 3: Rounded Modern Theme

```typescript
export const roundedModernTheme: Partial<ThemeConfig> = {
  borders: {
    radius: {
      base: '0.75rem',   // 12px
      lg: '1.25rem',     // 20px
      xl: '1.5rem',      // 24px
    },
  },
  shadows: {
    shadow: {
      base: '0 4px 20px 0 rgb(0 0 0 / 0.08)',
      lg: '0 10px 40px 0 rgb(0 0 0 / 0.12)',
    },
  },
};
```

---

## End of Document

**Summary:**
- ✅ Complete design system with design tokens
- ✅ Typography system with hierarchy
- ✅ Color system with semantic usage
- ✅ Spacing and layout patterns
- ✅ Component library (reusable components)
- ✅ Interaction patterns (loading, errors, toasts, confirmations)
- ✅ Page layout templates
- ✅ Responsive behavior patterns
- ✅ Accessibility standards (WCAG 2.1 AA)
- ✅ Full customization guide (logo, fonts, colors, spacing)

**Key Features:**
1. **Centralized Configuration** - All design tokens in one place
2. **Theme Provider** - Easy theme switching and customization
3. **Reusable Components** - Consistent UX across all pages
4. **Responsive First** - Mobile-first approach with breakpoints
5. **Accessible** - WCAG 2.1 Level AA compliant
6. **Customizable** - Easy to rebrand (logo, colors, fonts, spacing)

**Files to Create:**
- `frontend/src/config/theme.config.ts` - Theme type definitions
- `frontend/src/config/defaultTheme.ts` - Default theme values
- `frontend/src/config/customTheme.ts` - Client customizations
- `frontend/src/contexts/ThemeContext.tsx` - Theme provider
- `frontend/src/components/ui/Typography.tsx` - Typography component
- `frontend/src/components/ui/StatusBadge.tsx` - Status badge component
- `frontend/src/components/ui/Container.tsx` - Container component
- `frontend/src/components/common/StatsCard.tsx` - Stats card component
- `frontend/src/components/common/PageHeader.tsx` - Page header component
- `frontend/src/components/layouts/StandardPage.tsx` - Page template
- `frontend/src/components/layouts/AppShell.tsx` - App layout

**Next Steps:**
1. Review design system with team
2. Create theme configuration files
3. Build core reusable components
4. Apply design system to loader pages
5. Test responsive behavior across devices
6. Validate accessibility compliance
