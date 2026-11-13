const KEY = 'crewrun:recent-searches'
const MAX = 10

// 최근 검색어는 이 브라우저에만 남긴다(서버 API 없음). 저장소를 못 쓰면 빈 목록으로 본다.
export function loadRecentSearches(): string[] {
  try {
    const list: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(list) ? list.filter((v): v is string => typeof v === 'string') : []
  } catch {
    return []
  }
}

function save(list: string[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(list))
  } catch {
    // 저장소를 못 쓰면 이번 화면에서만 보인다
  }
  return list
}

/** 같은 검색어는 맨 앞으로 옮기고 10개까지만 남긴다 */
export function addRecentSearch(term: string): string[] {
  const trimmed = term.trim()
  if (trimmed === '') return loadRecentSearches()
  return save([trimmed, ...loadRecentSearches().filter((t) => t !== trimmed)].slice(0, MAX))
}

export function removeRecentSearch(term: string): string[] {
  return save(loadRecentSearches().filter((t) => t !== term))
}

export function clearRecentSearches(): string[] {
  return save([])
}
