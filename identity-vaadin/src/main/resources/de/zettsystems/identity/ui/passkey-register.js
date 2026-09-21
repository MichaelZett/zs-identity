// Registering a passkey, driven from PasskeyView through executeJs.
//
// Runs as the body of a function: $0 is the context path, $1 and $2 the
// header name and value of Spring Security's CSRF token (empty when CSRF is
// off), $3 the label the person gave the passkey. The returned promise
// resolves with "ok" once the server has stored the passkey, and rejects
// with one word that the view turns into a text: "unsupported", "cancelled"
// or "failed".
//
// The wire format is the one Spring Security's WebAuthn filters expect (see
// spring-security-webauthn.js in spring-security-web).
const contextPath = $0;
const csrfHeader = $1;
const csrfToken = $2;
const label = $3;

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
  const response = await fetch(contextPath + '/webauthn/register/options', {
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
    user: {
      ...options.user,
      id: base64url.decode(options.user.id)
    },
    challenge: base64url.decode(options.challenge),
    excludeCredentials: (options.excludeCredentials || []).map((credential) => ({
      ...credential,
      id: base64url.decode(credential.id)
    }))
  };
  return navigator.credentials.create({ publicKey });
}

async function store(credential) {
  const response = credential.response;
  const body = {
    publicKey: {
      credential: {
        id: credential.id,
        rawId: base64url.encode(credential.rawId),
        response: {
          attestationObject: base64url.encode(response.attestationObject),
          clientDataJSON: base64url.encode(response.clientDataJSON),
          transports: response.getTransports ? response.getTransports() : []
        },
        type: credential.type,
        clientExtensionResults: credential.getClientExtensionResults(),
        authenticatorAttachment: credential.authenticatorAttachment
      },
      label: label
    }
  };
  const answer = await fetch(contextPath + '/webauthn/register', {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify(body)
  });
  if (!answer.ok) {
    throw new Error('HTTP ' + answer.status);
  }
  const json = await answer.json();
  if (!json || !json.success) {
    throw new Error('Unexpected answer ' + JSON.stringify(json));
  }
}

async function run() {
  if (!window.PublicKeyCredential || !navigator.credentials) {
    throw 'unsupported';
  }
  let options;
  try {
    options = await fetchOptions();
  } catch (e) {
    console.warn('Passkey registration: no options', e);
    throw 'failed';
  }
  let credential;
  try {
    credential = await askAuthenticator(options);
  } catch (e) {
    const cancelled = e && (e.name === 'NotAllowedError' || e.name === 'AbortError');
    if (!cancelled) {
      console.warn('Passkey registration: authenticator failed', e);
    }
    throw cancelled ? 'cancelled' : 'failed';
  }
  try {
    await store(credential);
  } catch (e) {
    console.warn('Passkey registration: storing failed', e);
    throw 'failed';
  }
  return 'ok';
}

return run();
