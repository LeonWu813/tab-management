/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: '#D9C196',
        'primary-dark': '#c4a96e',
        secondary: '#f2be7e',
        highlight: '#f2836b',
        dark: '#0d0d0d',
        'app-bg': '#f2f1f0',
      },
    },
  },
  plugins: [],
}
