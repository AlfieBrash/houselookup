const BACKEND_URL = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? "http://localhost:8081";

export interface AuthMe {
  email: string;
  credits: number;
}

export interface AuthRequest {
  email: string;
  password: string;
}

const parseError = async (response: Response) => {
  const text = await response.text();
  return text || "Authentication request failed.";
};

export async function registerUser(payload: AuthRequest): Promise<AuthMe> {
  const response = await fetch(`${BACKEND_URL}/api/auth/register`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}

export async function loginUser(payload: AuthRequest): Promise<AuthMe> {
  const response = await fetch(`${BACKEND_URL}/api/auth/login`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}

export async function logoutUser(): Promise<void> {
  const response = await fetch(`${BACKEND_URL}/api/auth/logout`, {
    method: "POST",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(await parseError(response));
  }
}

export async function getCurrentUser(): Promise<AuthMe> {
  const response = await fetch(`${BACKEND_URL}/api/auth/me`, {
    credentials: "include",
  });

  if (response.status === 401) {
    throw new Error("AUTH_REQUIRED");
  }
  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}

export async function fetchCredits(): Promise<{ credits: number }> {
  const response = await fetch(`${BACKEND_URL}/api/payments/credits`, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(await parseError(response));
  }

  return response.json();
}
