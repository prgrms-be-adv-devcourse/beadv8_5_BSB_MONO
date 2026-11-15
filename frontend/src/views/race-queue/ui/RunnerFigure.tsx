// 피그마 대기열 · 러너 그림(1333:20617). 시안의 SVG를 그대로 옮겼다.
export function RunnerFigure({ className }: { className?: string }) {
  return (
    <svg
      width="96"
      height="96"
      viewBox="0 0 96 96"
      fill="none"
      aria-hidden
      className={className}
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        opacity="0.7"
        d="M48 94C62.3594 94 74 92.2091 74 90C74 87.7909 62.3594 86 48 86C33.6406 86 22 87.7909 22 90C22 92.2091 33.6406 94 48 94Z"
        fill="#C3CBDA"
      />
      <path
        d="M58 25C62.9706 25 67 20.9706 67 16C67 11.0294 62.9706 7 58 7C53.0294 7 49 11.0294 49 16C49 20.9706 53.0294 25 58 25Z"
        fill="#0057FF"
      />
      <path
        d="M52 30L38 52L54 60L46 84"
        strokeWidth="10"
        strokeLinecap="round"
        strokeLinejoin="round"
        stroke="#0038A3"
      />
      <path
        d="M52 28L38 50L54 58L46 82"
        strokeWidth="10"
        strokeLinecap="round"
        strokeLinejoin="round"
        stroke="#0057FF"
      />
      <path d="M38 52L26 74" strokeWidth="9" strokeLinecap="round" stroke="#0057FF" />
      <path
        d="M50 34L66 44L76 36"
        strokeWidth="8"
        strokeLinecap="round"
        strokeLinejoin="round"
        stroke="#0057FF"
      />
      <path
        d="M50 34L36 36L28 46"
        strokeWidth="8"
        strokeLinecap="round"
        strokeLinejoin="round"
        stroke="#7FA4FF"
      />
      <path
        d="M52 38H42C40.8954 38 40 38.8954 40 40V46C40 47.1046 40.8954 48 42 48H52C53.1046 48 54 47.1046 54 46V40C54 38.8954 53.1046 38 52 38Z"
        fill="#C6FF1F"
      />
    </svg>
  )
}
