export function createAppRouteUpdateGuards(options = {}) {
  const {
    loadRequestTracker,
    getCurrentRouteState,
  } = options;

  function isLatestRequest(scope, requestId) {
    return loadRequestTracker[scope] === requestId;
  }

  function isActiveRoute(routeKey, routeValue) {
    const routeState = getCurrentRouteState();
    if (routeState.key !== routeKey) {
      return false;
    }
    if (routeKey === "jobDetail" || routeKey === "jobConfig") {
      return String(routeState.jobKey || "") === String(routeValue || "");
    }
    if (routeKey === "runDetail") {
      return String(routeState.jobExecutionId || "") === String(routeValue || "");
    }
    return true;
  }

  function shouldApplyRouteScopedUpdate(routeKey, requestId, routeValue) {
    return isLatestRequest(routeKey, requestId) && isActiveRoute(routeKey, routeValue);
  }

  return {
    isActiveRoute,
    isLatestRequest,
    shouldApplyRouteScopedUpdate,
  };
}

