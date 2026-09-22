// ---------- Cognito auth (raw API calls - no SDK needed) ----------
// Cognito's User Pool API can be called directly over HTTPS as long as you
// only use actions that don't require a signed request (SignUp, ConfirmSignUp,
// InitiateAuth all work this way for a client with no secret).

const COGNITO_ENDPOINT = `https://cognito-idp.${CONFIG.COGNITO_REGION}.amazonaws.com/`;

async function cognitoRequest(target, body) {
  const res = await fetch(COGNITO_ENDPOINT, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-amz-json-1.1",
      "X-Amz-Target": `AWSCognitoIdentityProviderService.${target}`
    },
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.message || data.__type || "Request failed");
  }
  return data;
}

function signUp(email, password) {
  return cognitoRequest("SignUp", {
    ClientId: CONFIG.COGNITO_CLIENT_ID,
    Username: email,
    Password: password,
    UserAttributes: [{ Name: "email", Value: email }]
  });
}

function confirmSignUp(email, code) {
  return cognitoRequest("ConfirmSignUp", {
    ClientId: CONFIG.COGNITO_CLIENT_ID,
    Username: email,
    ConfirmationCode: code
  });
}

async function login(email, password) {
  const data = await cognitoRequest("InitiateAuth", {
    AuthFlow: "USER_PASSWORD_AUTH",
    ClientId: CONFIG.COGNITO_CLIENT_ID,
    AuthParameters: { USERNAME: email, PASSWORD: password }
  });
  return data.AuthenticationResult.IdToken;
}

// ---------- Session (kept in sessionStorage so a page refresh doesn't log you out) ----------

function saveSession(email, token) {
  sessionStorage.setItem("email", email);
  sessionStorage.setItem("idToken", token);
}

function getToken() {
  return sessionStorage.getItem("idToken");
}

function getEmail() {
  return sessionStorage.getItem("email");
}

function clearSession() {
  sessionStorage.removeItem("email");
  sessionStorage.removeItem("idToken");
}

// ---------- API calls to our own backend ----------

async function apiRequest(path, options = {}) {
  const res = await fetch(`${CONFIG.API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${getToken()}`,
      ...(options.headers || {})
    }
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.message || "Request failed");
  }
  return data;
}

function createShortUrl(longUrl) {
  return apiRequest("/urls", {
    method: "POST",
    body: JSON.stringify({ longUrl })
  });
}

function listMyUrls() {
  return apiRequest("/urls");
}

function getAnalytics(shortCode) {
  return apiRequest(`/analytics/${shortCode}`);
}

// ---------- UI wiring ----------

const authSection = document.getElementById("auth-section");
const dashboardSection = document.getElementById("dashboard-section");

const tabLogin = document.getElementById("tab-login");
const tabSignup = document.getElementById("tab-signup");
const loginForm = document.getElementById("login-form");
const signupForm = document.getElementById("signup-form");
const confirmForm = document.getElementById("confirm-form");

let pendingConfirmEmail = null;

tabLogin.addEventListener("click", () => switchTab("login"));
tabSignup.addEventListener("click", () => switchTab("signup"));

function switchTab(tab) {
  tabLogin.classList.toggle("active", tab === "login");
  tabSignup.classList.toggle("active", tab === "signup");
  loginForm.classList.toggle("hidden", tab !== "login");
  signupForm.classList.toggle("hidden", tab !== "signup");
  confirmForm.classList.add("hidden");
}

loginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const email = document.getElementById("login-email").value;
  const password = document.getElementById("login-password").value;
  const errorEl = document.getElementById("login-error");
  errorEl.textContent = "";

  try {
    const token = await login(email, password);
    saveSession(email, token);
    showDashboard();
  } catch (err) {
    errorEl.textContent = err.message;
  }
});

signupForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const email = document.getElementById("signup-email").value;
  const password = document.getElementById("signup-password").value;
  const errorEl = document.getElementById("signup-error");
  errorEl.textContent = "";

  try {
    await signUp(email, password);
    pendingConfirmEmail = email;
    signupForm.classList.add("hidden");
    confirmForm.classList.remove("hidden");
  } catch (err) {
    errorEl.textContent = err.message;
  }
});

confirmForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const code = document.getElementById("confirm-code").value;
  const errorEl = document.getElementById("confirm-error");
  errorEl.textContent = "";

  try {
    await confirmSignUp(pendingConfirmEmail, code);
    confirmForm.classList.add("hidden");
    switchTab("login");
    document.getElementById("login-email").value = pendingConfirmEmail;
  } catch (err) {
    errorEl.textContent = err.message;
  }
});

document.getElementById("logout-btn").addEventListener("click", () => {
  clearSession();
  dashboardSection.classList.add("hidden");
  authSection.classList.remove("hidden");
});

// ---------- Dashboard ----------

function showDashboard() {
  authSection.classList.add("hidden");
  dashboardSection.classList.remove("hidden");
  document.getElementById("user-email").textContent = getEmail();
  loadUrls();
}

const createUrlForm = document.getElementById("create-url-form");
createUrlForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const input = document.getElementById("long-url-input");
  const longUrl = input.value.trim();
  if (!longUrl) return;

  try {
    await createShortUrl(longUrl);
    input.value = "";
    await loadUrls();
  } catch (err) {
    alert("Failed to create short URL: " + err.message);
  }
});

async function loadUrls() {
  const listEl = document.getElementById("urls-list");
  listEl.innerHTML = `<li class="loading">Loading your links...</li>`;

  try {
    const urls = await listMyUrls();
    if (urls.length === 0) {
      listEl.innerHTML = `<li class="empty">No links yet - shorten one above.</li>`;
      return;
    }

    listEl.innerHTML = "";
    urls.forEach((url) => {
      const li = document.createElement("li");
      const shortLink = `${CONFIG.API_BASE_URL}/${url.shortCode}`;

      li.innerHTML = `
        <div class="url-row">
          <div>
            <a href="${shortLink}" target="_blank" class="url-short">${shortLink}</a>
            <span class="url-long">${url.longUrl}</span>
          </div>
          <button class="view-analytics-btn" data-code="${url.shortCode}">📊 Stats</button>
        </div>
      `;
      listEl.appendChild(li);
    });

    document.querySelectorAll(".view-analytics-btn").forEach((btn) => {
      btn.addEventListener("click", () => openAnalytics(btn.dataset.code));
    });
  } catch (err) {
    listEl.innerHTML = `<li class="empty">Failed to load links: ${err.message}</li>`;
  }
}

// ---------- Analytics modal ----------

const analyticsModal = document.getElementById("analytics-modal");
document.getElementById("close-modal").addEventListener("click", () => {
  analyticsModal.classList.add("hidden");
});

let chartInstance = null;

async function openAnalytics(shortCode) {
  analyticsModal.classList.remove("hidden");
  document.getElementById("analytics-title").textContent = `Stats for /${shortCode}`;
  document.getElementById("analytics-total").textContent = "Loading...";

  try {
    const data = await getAnalytics(shortCode);
    document.getElementById("analytics-total").textContent =
      `Total clicks: ${data.totalClicks}`;

    // Group clicks by day for the chart
    const clicksByDay = {};
    data.clicks.forEach((click) => {
      const day = click.clickedAt.split("T")[0];
      clicksByDay[day] = (clicksByDay[day] || 0) + 1;
    });

    const labels = Object.keys(clicksByDay).sort();
    const values = labels.map((day) => clicksByDay[day]);

    const ctx = document.getElementById("clicks-chart").getContext("2d");
    if (chartInstance) chartInstance.destroy();
    chartInstance = new Chart(ctx, {
      type: "bar",
      data: {
        labels: labels,
        datasets: [{
          label: "Clicks",
          data: values,
          backgroundColor: "#6366f1"
        }]
      },
      options: {
        scales: {
          y: { beginAtZero: true, ticks: { stepSize: 1 } }
        }
      }
    });
  } catch (err) {
    document.getElementById("analytics-total").textContent = "Failed to load: " + err.message;
  }
}

// ---------- On page load ----------

if (getToken()) {
  showDashboard();
}
