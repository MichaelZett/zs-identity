// Sign-in with a passkey, driven from LoginView through executeJs.
//
// Runs as the body of a function: $0 is the context path, $1 and $2 the
// header name and value of Spring Security's CSRF token (empty when CSRF is
// off), $3 the mode -- "" for the button, "conditional" for the suggestion in
// the username field. `this` is the element executeJs was called on; in the
// conditional mode that is the vaadin-login-form, which the script needs, so
// run() takes it as its parameter and hands it on.
// The returned promise resolves with "ok" once the browser is on its way to
// the page after sign-in, and rejects with an Error whose message is one word
// that the view turns into a text: "unsupported", "cancelled", "disabled" or
// "failed". The view looks for the word inside what it receives, so the
// "Error: " a browser may put in front does not matter.
//
// TWO MODES, ONE CEREMONY. The button asks; conditional only offers. So
// everything that is not a real sign-in failure resolves with "quiet" instead
// of rejecting when nobody asked -- no browser, no conditional support, no
// options, an ignored suggestion, the abort when the password wins. A message
// for a suggestion that was never taken up would be noise. What the person
// did start (a chosen passkey that the server turns down) still speaks.
//
// The wire format is the one Spring Security's WebAuthn filters expect (see
// spring-security-webauthn.js in spring-security-web): base64url without
// padding for every binary field, and the assertion under the same names
// the browser uses.
//
// "failed" carries the HTTP status when a request was turned down ("failed
// HTTP 400"); the view logs that and still reads the word out of it.
const contextPath = $0;
const csrfHeader = $1;
const csrfToken = $2;
const conditional = $3 === 'conditional';
const QUIET = 'quiet';

const base64url = {
  encode(buffer) {
    const base64 = window.btoa(String.fromCodePoint(...new Uint8Array(buffer)));
    return base64.replaceAll('=', '').replaceAll('+', '-').replaceAll('/', '_');
  },
  decode(text) {
    const base64 = text.replaceAll('-', '+').replaceAll('_', '/');
    const binary = window.atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.codePointAt(i);
    }
    return bytes.buffer;
  }
};

// An error that carries the status of a rejected request. A rejected
// WebAuthn endpoint is the one failure nobody can see from the outside: the
// filters answer with an empty body, and a browser console is not a server
// log. So the status rides along in the word the view gets.
function httpError(status) {
  const error = new Error('HTTP ' + status);
  error.status = status;
  return error;
}

// The catch-all code, with the status appended when there was one. The view
// looks for the code INSIDE the message (PasskeyScripts.errorCode), so only
// digits are ever appended -- never text that might hold another code.
function failed(e) {
  return new Error(e?.status ? 'failed HTTP ' + e.status : 'failed');
}

function headers() {
  const result = { 'Content-Type': 'application/json' };
  if (csrfHeader) {
    result[csrfHeader] = csrfToken;
  }
  return result;
}

async function fetchOptions() {
  const response = await fetch(contextPath + '/webauthn/authenticate/options', {
    method: 'POST',
    headers: headers()
  });
  if (!response.ok) {
    throw httpError(response.status);
  }
  return response.json();
}

async function askAuthenticator(options, signal) {
  const publicKey = {
    ...options,
    challenge: base64url.decode(options.challenge),
    allowCredentials: (options.allowCredentials || []).map((credential) => ({
      ...credential,
      id: base64url.decode(credential.id)
    }))
  };
  const request = { publicKey };
  if (conditional) {
    // The browser shows what it has in the field instead of opening a dialog,
    // and keeps waiting until someone picks one -- or until the signal fires.
    request.mediation = 'conditional';
    request.signal = signal;
  }
  return navigator.credentials.get(request);
}

async function conditionalSupported() {
  try {
    return typeof PublicKeyCredential.isConditionalMediationAvailable === 'function'
      && await PublicKeyCredential.isConditionalMediationAvailable();
  } catch (e) {
    return false;
  }
}

// A browser offers passkeys only in a field that says it takes them.
// vaadin-login-form renders its form into its own light DOM, and
// `autocomplete` is among the delegateAttrs of Vaadin's InputFieldMixin, so
// the value reaches the native input -- no shadow root to reach through.
// Checked against @vaadin/login 25.2.6; the template is not API, so look
// again when Vaadin moves.
function offerPasskeyInUsernameField(host) {
  const field = host.querySelector('#vaadinLoginUsername');
  if (field) {
    field.setAttribute('autocomplete', 'username webauthn');
  }
}

// The waiting request has to end the moment the person goes the other way,
// or the next ceremony runs into "a request is already pending". The password
// is caught here through the component's own `login` event (it fires for the
// button and for Enter alike); the passkey button is server-side and aborts
// through PasskeyScripts before it starts.
function armAbort(host) {
  const controller = new AbortController();
  host.__zsPasskeyConditional = controller;
  host.addEventListener('login', () => controller.abort(), { once: true });
  return controller.signal;
}

function disarmAbort(host) {
  host.__zsPasskeyConditional = null;
}

async function signIn(credential) {
  const response = credential.response;
  const body = {
    id: credential.id,
    rawId: base64url.encode(credential.rawId),
    response: {
      authenticatorData: base64url.encode(response.authenticatorData),
      clientDataJSON: base64url.encode(response.clientDataJSON),
      signature: base64url.encode(response.signature),
      userHandle: response.userHandle ? base64url.encode(response.userHandle) : undefined
    },
    type: credential.type,
    clientExtensionResults: credential.getClientExtensionResults(),
    authenticatorAttachment: credential.authenticatorAttachment
  };
  const answer = await fetch(contextPath + '/login/webauthn', {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify(body)
  });
  const json = await answer.json().catch(() => ({}));
  if (answer.status === 401) {
    return { reason: json.reason === 'disabled' ? 'disabled' : 'failed' };
  }
  if (!answer.ok || !json.authenticated || !json.redirectUrl) {
    return { reason: answer.ok ? 'failed' : 'failed HTTP ' + answer.status };
  }
  return { redirectUrl: json.redirectUrl };
}

// The page after sign-in comes from the server, but only ever within this
// origin: a redirect target taken from a response is exactly how an open
// redirect gets in, and Spring's saved request is always one of ours anyway.
function sameOriginTarget(url) {
  const target = new URL(url, window.location.href);
  return target.origin === window.location.origin ? target.href : contextPath + '/';
}

// Whether there is anything to do at all. Null means "stay quiet"; otherwise
// the abort signal the conditional request waits on (undefined for the button).
async function prepare(host) {
  if (!window.PublicKeyCredential || !navigator.credentials) {
    if (conditional) {
      return null;
    }
    throw new Error('unsupported');
  }
  if (!conditional) {
    return { signal: undefined };
  }
  if (!(await conditionalSupported())) {
    return null;
  }
  offerPasskeyInUsernameField(host);
  return { signal: armAbort(host) };
}

// The options, or null when a mere suggestion could not get any.
async function optionsOrQuiet(host) {
  try {
    return await fetchOptions();
  } catch (e) {
    console.warn('Passkey sign-in: no options', e);
    if (conditional) {
      disarmAbort(host);
      return null;
    }
    throw failed(e);
  }
}

// The chosen passkey, or null when a suggestion was not taken up.
async function credentialOrQuiet(host, options, signal) {
  try {
    return await askAuthenticator(options, signal);
  } catch (e) {
    const cancelled = e?.name === 'NotAllowedError' || e?.name === 'AbortError';
    if (!cancelled) {
      console.warn('Passkey sign-in: authenticator failed', e);
    }
    // An offer nobody took up, and the abort when the password wins, both
    // land here. Neither is a failure worth a text.
    if (conditional) {
      disarmAbort(host);
      return null;
    }
    throw new Error(cancelled ? 'cancelled' : 'failed');
  }
}

async function run(host) {
  const prepared = await prepare(host);
  if (!prepared) {
    return QUIET;
  }
  const options = await optionsOrQuiet(host);
  if (!options) {
    return QUIET;
  }
  const credential = await credentialOrQuiet(host, options, prepared.signal);
  if (!credential) {
    return QUIET;
  }
  // From here on the person has chosen this passkey, so a failure speaks
  // even when the ceremony began as a mere suggestion.
  disarmAbort(host);
  let outcome;
  try {
    outcome = await signIn(credential);
  } catch (e) {
    console.warn('Passkey sign-in: request failed', e);
    throw failed(e);
  }
  if (outcome.reason) {
    throw new Error(outcome.reason);
  }
  window.location.href = sameOriginTarget(outcome.redirectUrl);
  return 'ok';
}

// `this` is the element executeJs was called on; see the top.
return run(this);
