import React from 'react';

interface EmblemProps {
  size?: number;
  className?: string;
}

/**
 * 1. Application Launcher Icon (108dp adaptive icon geometry)
 */
export function SpeedoraLauncherSvg({ size = 72, className = '' }: EmblemProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 108 108"
      fill="none"
      className={className}
      aria-label="SPEEDORA Launcher Icon"
    >
      <rect width="108" height="108" rx="26" fill="#1769D2" />
      <path d="M0 54C0 24.18 24.18 0 54 0H108V108H0V54Z" fill="#104FA8" fillOpacity="0.42" />
      <path d="M30 54A24 24 0 0 1 54 30" stroke="#EAF3FF" strokeWidth="4.2" strokeLinecap="round" />
      <path d="M78 54A24 24 0 0 1 54 78" stroke="#EAF3FF" strokeWidth="4.2" strokeLinecap="round" />
      <path d="M38 54A16 16 0 0 1 54 38" stroke="#FFFFFF" strokeWidth="4.2" strokeLinecap="round" />
      <path d="M70 54A16 16 0 0 1 54 70" stroke="#FFFFFF" strokeWidth="4.2" strokeLinecap="round" />
      <path d="M43 54L54 43H68L57 54H65L54 65H40L51 54H43Z" fill="#FFFFFF" />
      <path d="M54 50.5L57.5 54L54 57.5L50.5 54L54 50.5Z" fill="#1769D2" />
    </svg>
  );
}

/**
 * 2. Main Brand Emblem (White surface variant)
 */
export function SpeedoraBrandEmblemSvg({ size = 40, className = '' }: EmblemProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      fill="none"
      className={className}
      aria-label="SPEEDORA Brand Emblem"
    >
      <path d="M8 24A16 16 0 0 1 24 8" stroke="#1769D2" strokeOpacity="0.32" strokeWidth="3" strokeLinecap="round" />
      <path d="M40 24A16 16 0 0 1 24 40" stroke="#1769D2" strokeOpacity="0.32" strokeWidth="3" strokeLinecap="round" />
      <path d="M13 24A11 11 0 0 1 24 13" stroke="#1769D2" strokeWidth="3" strokeLinecap="round" />
      <path d="M35 24A11 11 0 0 1 24 35" stroke="#1769D2" strokeWidth="3" strokeLinecap="round" />
      <path d="M15 24L24 15H34L25.5 24H33L24 33H14L22.5 24H15Z" fill="#102B50" />
      <path d="M24 21.5L26.5 24L24 26.5L21.5 24L24 21.5Z" fill="#FFFFFF" />
    </svg>
  );
}

/**
 * 3. Startup Splash Emblem
 */
export function SpeedoraStartupEmblemSvg({ size = 72, className = '' }: EmblemProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 80 80"
      fill="none"
      className={className}
      aria-label="SPEEDORA Startup Emblem"
    >
      <circle cx="40" cy="40" r="37" fill="#EAF3FF" stroke="#DCE6F4" strokeWidth="1.5" />
      <circle cx="40" cy="40" r="27" fill="#FFFFFF" />
      <path d="M19 40A21 21 0 0 1 40 19" stroke="#1769D2" strokeWidth="3.2" strokeLinecap="round" />
      <path d="M61 40A21 21 0 0 1 40 61" stroke="#1769D2" strokeWidth="3.2" strokeLinecap="round" />
      <path d="M26 40A14 14 0 0 1 40 26" stroke="#104FA8" strokeWidth="3.2" strokeLinecap="round" />
      <path d="M54 40A14 14 0 0 1 40 54" stroke="#104FA8" strokeWidth="3.2" strokeLinecap="round" />
      <path d="M30 40L40 30H52L42.5 40H50L40 50H28L37.5 40H30Z" fill="#102B50" />
      <path d="M40 37.2L42.8 40L40 42.8L37.2 40L40 37.2Z" fill="#FFFFFF" />
    </svg>
  );
}

/**
 * 4. Transfer Animation Emblem
 */
export function SpeedoraTransferEmblemSvg({ size = 72, className = '' }: EmblemProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 80 80"
      fill="none"
      className={className}
      aria-label="SPEEDORA Transfer Emblem"
    >
      <circle cx="40" cy="40" r="36" fill="#EAF3FF" />
      <circle cx="40" cy="40" r="28" stroke="#1769D2" strokeWidth="2" strokeDasharray="6 4" />
      <path d="M27 36L40 23H53L42 36H27Z" fill="#1769D2" />
      <path d="M53 44L40 57H27L38 44H53Z" fill="#102B50" />
      <path d="M40 36.5L43.5 40L40 43.5L36.5 40L40 36.5Z" fill="#104FA8" />
    </svg>
  );
}

/**
 * 5. Small Navigation Icon (24dp vector)
 */
export function SpeedoraNavIconSvg({ size = 24, className = '' }: EmblemProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      className={className}
      aria-label="SPEEDORA Nav Icon"
    >
      <path d="M4 12A8 8 0 0 1 12 4" stroke="#1769D2" strokeWidth="2" strokeLinecap="round" />
      <path d="M20 12A8 8 0 0 1 12 20" stroke="#1769D2" strokeWidth="2" strokeLinecap="round" />
      <path d="M7.5 12L12 7.5H17.5L13 12H16.5L12 16.5H6.5L11 12H7.5Z" fill="#102B50" />
    </svg>
  );
}
