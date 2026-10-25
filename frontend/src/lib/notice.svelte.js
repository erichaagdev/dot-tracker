// The message that pops up when a change is refused, saying what happened and what to do next.

const showFor = 8000

export const notice = $state({ message: '' })

let timer

export function showNotice(message) {
  notice.message = message
  clearTimeout(timer)
  timer = setTimeout(dismissNotice, showFor)
}

export function dismissNotice() {
  clearTimeout(timer)
  notice.message = ''
}
