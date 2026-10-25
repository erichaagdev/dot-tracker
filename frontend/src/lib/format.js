// How prices, dates, names and product line colors show on screen.

const dollars = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export function price(cents) {
  return dollars.format(cents / 100)
}

/** A date such as "September 28", with the year added when it isn't this year. */
export function day(date) {
  const sameYear = date.getFullYear() === new Date().getFullYear()
  return date.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: sameYear ? undefined : 'numeric' })
}

/** An ISO date such as 2026-09-28, read as that day where the person is rather than as midnight in UTC. */
export function isoDay(text) {
  const [year, month, date] = text.split('-').map(Number)
  return day(new Date(year, month - 1, date))
}

/** One or two letters for someone's avatar, from their display name. */
export function initialsOf(name) {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean)
  const letters = words.length > 1 ? words[0][0] + words.at(-1)[0] : (words[0]?.[0] ?? '?')
  return letters.toUpperCase()
}

/** The class that gives a product line's covers and swatches its color family, such as line-trace for TRACE. */
export function lineClass(line) {
  return line ? `line-${line.color.toLowerCase()}` : ''
}

/** A store's link when it's a web address, and null otherwise, so a link can only ever open a web page. */
export function webLink(url) {
  return url && /^https?:\/\//i.test(url) ? url : null
}
