package mall.user.exception.custom;

public class InvalidPasswordException extends RuntimeException {

	public InvalidPasswordException() {
		super("Invalid password");
	}

}
