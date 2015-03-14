package sandbox;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class BazControllerAdvice {

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
	}

	@ExceptionHandler
	@ResponseStatus(NOT_FOUND)
	@ResponseBody
	public ErrorResponse handle(EntityNotFoundException ex) {
		return new ErrorResponse(ex.getClass().getSimpleName(), ex.getMessage());
	}
}
