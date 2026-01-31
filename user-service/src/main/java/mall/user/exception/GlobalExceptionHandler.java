package mall.user.exception;

import mall.user.exception.custom.DuplicateUserException;
import mall.user.exception.custom.InvalidPasswordException;
import mall.user.exception.custom.TokenInvalidException;
import mall.user.exception.custom.UserNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleUserNotFoundException() {
		return ResponseEntity.status(ErrorCode.USER_NOT_FOUND.getStatus())
			.body(new ErrorResponse(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage()));
	}

	@ExceptionHandler(InvalidPasswordException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPasswordException() {
		return ResponseEntity.status(ErrorCode.INVALID_PASSWORD.getStatus())
			.body(new ErrorResponse(ErrorCode.INVALID_PASSWORD.getCode(), ErrorCode.INVALID_PASSWORD.getMessage()));
	}

	@ExceptionHandler(TokenInvalidException.class)
	public ResponseEntity<ErrorResponse> handleTokenInvalidException() {
		return ResponseEntity.status(ErrorCode.TOKEN_INVALID.getStatus())
			.body(new ErrorResponse(ErrorCode.TOKEN_INVALID.getCode(), ErrorCode.TOKEN_INVALID.getMessage()));
	}

	@ExceptionHandler(DuplicateUserException.class)
	public ResponseEntity<ErrorResponse> handleDuplicateUserException() {
		return ResponseEntity.status(ErrorCode.DUPLICATE_USER.getStatus())
			.body(new ErrorResponse(ErrorCode.DUPLICATE_USER.getCode(), ErrorCode.DUPLICATE_USER.getMessage()));
	}

	// 나머지 Exception handler
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception e) {
		return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
			.body(new ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
					ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
	}

}
