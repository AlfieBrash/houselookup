interface ApiErrorBody {
  message?: unknown;
  requestId?: unknown;
}

export const parseApiError = async (response: Response, fallbackMessage: string) => {
  const responseRequestId = response.headers.get("X-Request-Id");
  const text = await response.text();

  if (!text) {
    return withRequestId(fallbackMessage, responseRequestId);
  }

  try {
    const body = JSON.parse(text) as ApiErrorBody;
    const message =
      typeof body.message === "string" && body.message.trim()
        ? body.message.trim()
        : fallbackMessage;
    const requestId =
      typeof body.requestId === "string" && body.requestId.trim()
        ? body.requestId.trim()
        : responseRequestId;
    return withRequestId(message, requestId);
  } catch {
    return withRequestId(text, responseRequestId);
  }
};

const withRequestId = (message: string, requestId?: string | null) => {
  if (!requestId) {
    return message;
  }
  return `${message} (request ${requestId})`;
};
