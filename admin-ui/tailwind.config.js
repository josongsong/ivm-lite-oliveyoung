/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  corePlugins: {
    preflight: false, // Disable Tailwind's base reset to preserve existing styles
  },
  theme: {
    extend: {
      colors: {
        // Dark Theme - Cyberpunk/Terminal Aesthetic
        bg: {
          primary: '#0a0a0f',
          secondary: '#12121a',
          tertiary: '#1a1a25',
          card: '#15151f',
          hover: '#1e1e2d',
        },
        border: {
          DEFAULT: '#2a2a3d',
          accent: '#3d3d5c',
        },
        text: {
          primary: '#e8e8f0',
          secondary: '#a0a0b8',
          muted: '#6a6a80',
        },
        accent: {
          cyan: '#00d4ff',
          magenta: '#ff00aa',
          green: '#00ff88',
          orange: '#ff8800',
          purple: '#8855ff',
          yellow: '#ffcc00',
        },
        status: {
          success: '#00ff88',
          warning: '#ffcc00',
          error: '#ff4466',
          info: '#00d4ff',
          pending: '#ff8800',
          processing: '#8855ff',
        },
      },
      fontFamily: {
        sans: ['Outfit', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      borderRadius: {
        sm: '6px',
        md: '10px',
        lg: '16px',
        xl: '24px',
      },
      boxShadow: {
        sm: '0 2px 8px rgba(0, 0, 0, 0.3)',
        md: '0 4px 20px rgba(0, 0, 0, 0.4)',
        lg: '0 8px 40px rgba(0, 0, 0, 0.5)',
        'glow-cyan': '0 0 20px rgba(0, 212, 255, 0.3)',
        'glow-magenta': '0 0 20px rgba(255, 0, 170, 0.3)',
      },
      backgroundImage: {
        'gradient-primary': 'linear-gradient(135deg, #00d4ff 0%, #8855ff 50%, #ff00aa 100%)',
        'gradient-card': 'linear-gradient(145deg, rgba(26, 26, 37, 0.8) 0%, rgba(21, 21, 31, 0.9) 100%)',
        'gradient-glow': 'radial-gradient(ellipse at center, rgba(0, 212, 255, 0.1) 0%, transparent 70%)',
      },
    },
  },
  plugins: [],
}
