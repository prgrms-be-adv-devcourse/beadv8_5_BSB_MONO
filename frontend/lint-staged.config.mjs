const config = {
  '*.{ts,tsx,mjs,mts}': ['eslint --fix', 'prettier --write'],
  '*.{json,css,yml,yaml}': 'prettier --write',
}

export default config
