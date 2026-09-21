const loginView = document.getElementById("loginView");
const mainView = document.getElementById("mainView");
const loginForm = document.getElementById("loginForm");
const loginError = document.getElementById("loginError");
const currentUser = document.getElementById("currentUser");
const welcomeText = document.getElementById("welcomeText");
const logoutBtn = document.getElementById("logoutBtn");
const switchUserBtn = document.getElementById("switchUserBtn");
const menuItems = document.querySelectorAll(".menu-item");
const sectionTitle = document.getElementById("sectionTitle");
const contentArea = document.getElementById("contentArea");

function showLogin(msg=""){
  mainView.classList.add("hidden");
  loginView.classList.remove("hidden");
  loginError.textContent = msg;
  loginError.classList.toggle("hidden", !msg);
}
function showMain(){
  loginView.classList.add("hidden");
  mainView.classList.remove("hidden");
}
function renderSection(section){
  sectionTitle.textContent = section.charAt(0).toUpperCase() + section.slice(1);
  const templates = {
    dashboard: `<div class="card"><h3>Dashboard</h3><p>Vista principal del sistema.</p></div>`,
    employees: `<div class="card"><h3>Empleados</h3><p>Listado y offboarding.</p></div>`,
    users: `<div class="card"><h3>Usuarios</h3><p>Gestión de usuarios existentes.</p></div>`,
    reports: `<div class="card"><h3>Reportes</h3><p>Reportes y exportaciones.</p></div>`
  };
  contentArea.innerHTML = templates[section] || templates.dashboard;
  menuItems.forEach(btn => btn.classList.toggle("active", btn.dataset.section === section));
}

async function loginFormPost(username, password){
  const body = new URLSearchParams();
  body.append("username", username);
  body.append("password", password);

  const r = await fetch("/login", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
    redirect: "follow"
  });

  // Si terminó en /login?error -> credencial inválida
  if (r.url && r.url.includes("/login?error")) {
    throw new Error("Credenciales inválidas");
  }

  // Si sigue en login sin cambiar -> algo falló
  if (r.url && /\/login(\?|$)/.test(r.url)) {
    throw new Error("Login no aceptado por backend");
  }

  if (!r.ok) throw new Error("Backend respondió " + r.status);

  return true;
}

async function doLogout(){
  try { await fetch("/logout", { method:"POST", credentials:"include" }); } catch {}
  showLogin();
}

loginForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const username = document.getElementById("username").value.trim();
  const password = document.getElementById("password").value;
  try {
    await loginFormPost(username, password);
    currentUser.textContent = "Usuario: " + username;
    welcomeText.textContent = "Bienvenido, " + username;
    showMain();
    renderSection("dashboard");
  } catch (err) {
    showLogin(err.message || "No se pudo autenticar con el backend actual");
  }
});

logoutBtn?.addEventListener("click", doLogout);
switchUserBtn?.addEventListener("click", doLogout);
menuItems.forEach(btn => btn.addEventListener("click", () => renderSection(btn.dataset.section)));

showLogin();
