// Every call to the backend. Writes carry the CSRF token from the XSRF-TOKEN cookie in the X-XSRF-TOKEN header. A
// refused request throws an ApiError whose message is the ProblemDetail's detail, written to be shown as is, and a 401
// tells the listener from onSignedOut so the app can show the sign-in screen.

const offline = "We couldn't reach Dot Tracker. Check your connection and try again."
const refused = "That didn't go through. Refresh the page and try again."
const signedOut = 'You were signed out. Sign in again to keep going.'

export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

let signedOutListener = () => {}

export function onSignedOut(listener) {
  signedOutListener = listener
}

export const get = (path) => request('GET', path)
export const post = (path, body) => request('POST', path, body)
export const put = (path, body) => request('PUT', path, body)
export const del = (path) => request('DELETE', path)

async function request(method, path, body) {
  const headers = { Accept: 'application/json' }
  if (method !== 'GET') {
    headers['X-XSRF-TOKEN'] = csrfToken()
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  let response
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    })
  } catch {
    throw new ApiError(offline, 0)
  }
  if (response.status === 401) {
    signedOutListener()
    throw new ApiError(signedOut, 401)
  }
  if (!response.ok) {
    throw new ApiError(await problemDetail(response), response.status)
  }
  return response.status === 204 ? null : response.json()
}

async function problemDetail(response) {
  try {
    const problem = await response.json()
    return typeof problem.detail === 'string' && problem.detail ? problem.detail : refused
  } catch {
    return refused
  }
}

function csrfToken() {
  const cookie = document.cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : ''
}
