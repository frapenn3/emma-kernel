package it.emma.kernel.api;

import it.emma.kernel.dto.ApiError;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {

  @Override
  public Response toResponse(Exception exception) {
    if (exception instanceof ApiException api) {
      return Response.status(api.status)
          .entity(new ApiError(api.code, api.getMessage()))
          .build();
    }
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(new ApiError("INTERNAL_ERROR", exception.toString()))
        .build();
  }
}
