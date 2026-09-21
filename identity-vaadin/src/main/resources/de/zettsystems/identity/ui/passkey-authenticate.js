// Sign-in with a passkey, driven from LoginView through executeJs.
//
// Runs as the body of a function: $0 is the context path, $1 and $2 the
// header name and value of Spring Security's CSRF token (empty when CSRF is
// off). The returned promise resolves with "ok" once the browser is on its
// way to the page after sign-in, and rejects with one word that the view
// turns into a text: "unsupported", "cancelled", "disabled" or "failed".
//
// The wire format is the one Spring Security's WebAuthn filters expect (see
// spring-security-webauthn.js in spring-security-web): base64url without
// padding for every binary field, and the assertion under the same names
// the browser uses.
const contextPath = $0;
const csrfHeader = $1;
const csrfToken = $2;

const base64url = {
  encode(buffer) {
    const base64 = window.btoa(String.fromCharCode(...new Uint8Array(buffer)));
    return base64.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  },
  decode(text) {
    const base64 = text.replace(/-/g, '+').replace(/_/g, '/');
    const binary = window.atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }
};

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
    throw new Error('HTTP ' + response.status);
  }
  return response.json();
}

async function askAuthenticator(options) {
  const publicKey = {
    ...options,
    challenge: base64url.decode(options.challenge),
    allowCredentials: (options.allowCredentials || []).map((credential) => ({
      ...credential,
      id: base64url.decode(credential.id)
    }))
  };
  return navigator.credentials.get({ publicKey });
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
    return { reason: 'failed' };
  }
  return { redirectUrl: json.redirectUrl };
}

async function run() {
  if (!window.PublicKeyCredential || !navigator.credentials) {
    throw 'unsupported';
  }
  let options;
  try {
    options = await fetchOptions();
  } catch (e) {
    console.warn('Passkey sign-in: no options', e);
    throw 'failed';
  }
  let credential;
  try {
    credential = await askAuthenticator(options);
  } catch (e) {
    const cancelled = e && (e.name === 'NotAllowedError' || e.name === 'AbortError');
    if (!cancelled) {
      console.warn('Passkey sign-in: authenticator failed', e);
    }
    throw cancelled ? 'cancelled' : 'failed';
  }
  let outcome;
  try {
    outcome = await signIn(credential);
  } catch (e) {
    console.warn('Passkey sign-in: request failed', e);
    throw 'failed';
  }
  if (outcome.reason) {
    throw outcome.reason;
  }
  window.location.href = outcome.redirectUrl;
  return 'ok';
}

return run();
