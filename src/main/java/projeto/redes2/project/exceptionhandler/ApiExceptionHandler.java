package projeto.redes2.project.exceptionhandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;

import projeto.redes2.project.exception.EntityAlreadyExists;
import projeto.redes2.project.exception.EntityInUse;
import projeto.redes2.project.exception.EntityNotFound;
import projeto.redes2.project.exception.EntityNotFoundInTheAppeal;
import projeto.redes2.project.exception.PropertyNotExist;

@ControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler{
	
	private final HttpStatus STTS_NOT_FOUND = HttpStatus.NOT_FOUND;
	private final HttpStatus STTS_BAD_REQUEST = HttpStatus.BAD_REQUEST;
	private final HttpStatus STTS_CONFLICT = HttpStatus.CONFLICT;
	
	@Autowired 
	private MessageSource messageSource;
	
	@ExceptionHandler(EntityNotFoundInTheAppeal.class)
	public ResponseEntity<?> handleEntityNotFoundInTheAppeal(EntityNotFoundInTheAppeal e, WebRequest request){
		Problem problem = handleProblem( STTS_NOT_FOUND, ProblemType.RESOURCE_NOT_FOUND, e.getMessage(), UserMessageProblem.SYSTEM_ERROR.getUserMessage());	
		return handleExceptionInternal(e, problem, new HttpHeaders(), STTS_NOT_FOUND, request);
	}
	
	@ExceptionHandler(EntityNotFound.class)
	public ResponseEntity<?> handleEntityNotFound(EntityNotFound e, WebRequest request){
		Problem problem = handleProblem(STTS_BAD_REQUEST, ProblemType.RESOURCE_NOT_FOUND, e.getMessage(), UserMessageProblem.SYSTEM_ERROR.getUserMessage());
		return handleExceptionInternal(e, problem, new HttpHeaders(), STTS_BAD_REQUEST, request);
	}
	
	//MUDAR NO REPOSITORIO, CRIAR QUERY PARA VERIFICAR NOME COM ID DO USUARIO
	@ExceptionHandler(EntityAlreadyExists.class)
	public ResponseEntity<?> handleEntityAlreadyExists(EntityAlreadyExists e, WebRequest request){
		Problem problem = handleProblem(STTS_BAD_REQUEST, ProblemType.ENTITY_ALREADY_EXISTS, e.getMessage(), e.getMessage());
		return handleExceptionInternal(e, problem, new HttpHeaders(), STTS_BAD_REQUEST, request);
	}
	
	@ExceptionHandler(EntityInUse.class)
	public ResponseEntity<?> handleEntityInUse(EntityInUse e, WebRequest request){
		Problem problem = handleProblem(STTS_BAD_REQUEST, ProblemType.ENTITY_IN_USE, e.getMessage(), UserMessageProblem.ENTITY_IN_USE.getUserMessage());
		return handleExceptionInternal(e, problem, new HttpHeaders(), STTS_BAD_REQUEST, request);
	}
	
	@ExceptionHandler(PropertyNotExist.class)
	public ResponseEntity<?> handlePropertyNotExists(PropertyNotExist e, WebRequest request){
		if(e.getCause() instanceof DataIntegrityViolationException)
			System.out.println(e.getCause());
		Problem problem = handleProblem(STTS_CONFLICT, ProblemType.PROPERTY_NOT_EXIST, e.getMessage(), UserMessageProblem.SYSTEM_ERROR.getUserMessage());
		return handleExceptionInternal(e, problem, new HttpHeaders(), STTS_CONFLICT, request);
	}
	
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, WebRequest request){
		String detail = String.format("The URl parameter '%s' received the value '%s', which is an invalid type."
				+ " Correct and enter a value compatible with type 'Long'", e.getName(), e.getValue());
		Problem problem = handleProblem(STTS_BAD_REQUEST, ProblemType.INVALID_PARAMETER, detail, UserMessageProblem.SYSTEM_ERROR.getUserMessage());
		
		return handleExceptionInternal(e, problem, new HttpHeaders(), STTS_BAD_REQUEST, request);
	}
	
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleExceptionCustom(Exception e, WebRequest request){
		Problem problem = handleProblem(HttpStatus.INTERNAL_SERVER_ERROR, ProblemType.INTERNAL_SERVER_ERROR, UserMessageProblem.SYSTEM_ERROR.getUserMessage()
				, UserMessageProblem.SYSTEM_ERROR.getUserMessage());
		return handleExceptionInternal(e, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}
	
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		List<Problem.Field> problemFields = e.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> {
					String message = messageSource.getMessage(fieldError, LocaleContextHolder.getLocale());
					return new Problem.Field(fieldError.getField(), message);
				})
				.collect(Collectors.toList());
		
		String detail = "One or more fields are invalid. Fill in correctly and try again.";
		Problem problem = handleProblemWithProblemFields(STTS_BAD_REQUEST, ProblemType.INVALID_DATA, detail, detail, problemFields);
		return handleExceptionInternal(e, problem, new HttpHeaders(), STTS_BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		Throwable rootCause = e.getCause();
		
		if(rootCause instanceof InvalidFormatException) { //valor de entrada de atributo não correspondido p
			return handleInvalidFormatException((InvalidFormatException) rootCause, headers, status, request);
		}else if(rootCause instanceof PropertyBindingException) { //valor da propriedade de algum atributo que não existe
			return handlePropertyBindingException((PropertyBindingException) rootCause, headers, status, request);
		}
		Problem problem = handleProblem(status, ProblemType.INCOMPREHENSIBLE_MESSAGE, "The request body is invalid. Check syntax error.", UserMessageProblem.SYSTEM_ERROR.getUserMessage());
		return handleExceptionInternal(e, problem, new HttpHeaders(), status, request);
	}
	
	private ResponseEntity<Object> handlePropertyBindingException(PropertyBindingException e, HttpHeaders headers, HttpStatus status, WebRequest request) {
		String path = e.getPath().stream().map(p -> p.getFieldName()).collect(Collectors.joining("."));
		String detail = String.format("Property '%s' does not exist. Correct and enter a valid property.", path);
		
		Problem problem = handleProblem(status, ProblemType.PROPERTY_NOT_EXIST, detail, UserMessageProblem.SYSTEM_ERROR.getUserMessage());
		return handleExceptionInternal(e, problem, headers, status, request);
	}

	public ResponseEntity<Object> handleInvalidFormatException(InvalidFormatException e, HttpHeaders headers, HttpStatus status, WebRequest request){
		String path = e.getPath().stream().map(p -> p.getFieldName()).collect(Collectors.joining("."));		
		String detail = String.format("Property '%s' received value '%s', which is of invalid type. Correct and enter a value compatible with type '%s'."
				, path, e.getValue(), e.getTargetType().getSimpleName());
		
		Problem problem = handleProblem(status, ProblemType.INCOMPREHENSIBLE_MESSAGE, detail, UserMessageProblem.SYSTEM_ERROR.getUserMessage());
		return handleExceptionInternal(e, problem, headers, status, request);
	}
	
	@Override
	protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException e, HttpHeaders headers,
			HttpStatus status, WebRequest request) {
		String detail = String.format("The resource '%s' you tried to access does not exist.", e.getRequestURL());
		Problem problem = handleProblem(status, ProblemType.RESOURCE_NOT_FOUND, detail, UserMessageProblem.SYSTEM_ERROR.getUserMessage());
		return handleExceptionInternal(e, problem, headers, status, request);
	}
	
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
			HttpStatus status, WebRequest request) {
		
		if(body == null) {
			body = new Problem(LocalDateTime.now(), status.value(), null, status.getReasonPhrase(), null, null, null);			
		}else if(body instanceof String) {
			body = new Problem(LocalDateTime.now(), status.value(), null, (String) body, null, null, null);
		}
		return super.handleExceptionInternal(ex, body, headers, status, request);
	}
	
	private Problem handleProblem(HttpStatus status, ProblemType problemType, String detail, String userMessage) {
		return new Problem(LocalDateTime.now(), status.value(), problemType.getUri(), problemType.getTitle(), detail, userMessage, null);
	}
	
	private Problem handleProblemWithProblemFields(HttpStatus status, ProblemType problemType, String detail, String userMessage, List<Problem.Field> problemFields) {
		return new Problem(LocalDateTime.now(), status.value(), problemType.getUri(), problemType.getTitle(), detail, userMessage, problemFields);
	}
}
