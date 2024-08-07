package projeto.redes2.project.service;

import java.lang.reflect.Field;
import java.util.Map;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import projeto.redes2.project.dto.ResponseDTO;
import projeto.redes2.project.dto.UserDTO;
import projeto.redes2.project.enums.Roles;
import projeto.redes2.project.exception.EntityAlreadyExists;
import projeto.redes2.project.exception.EntityInUse;
import projeto.redes2.project.exception.EntityNotFoundInTheAppeal;
import projeto.redes2.project.model.User;
import projeto.redes2.project.repository.UserRepository;
import projeto.redes2.project.security.TokenService;

@Service
@RequiredArgsConstructor
public class UserService {
	
	private final UserRepository repository;
	private Field field;
	private final TokenService tokenService;
	private final PasswordEncoder passwordEncoder;

	public User find(Long id) {
		return repository.findById(id).orElseThrow(() -> new EntityNotFoundInTheAppeal(String.format("User '%s' not unregistered.", id)));
	}

	@Transactional
	public ResponseDTO register(User user) {
		if(repository.findByName(user.getName()) == null) {
			if(repository.findByEmail(user.getEmail()) != null) {
				throw new EntityAlreadyExists(String.format("Email '%s' is already registered.", user.getEmail()));
			}
			user.setPassword(passwordEncoder.encode(user.getPassword()));
			user.setRole(Roles.USER.toString());
			String token = this.tokenService.generateToken(user);
			var userDTO = UserDTO.fromEntity(repository.save(user));	
			return new ResponseDTO(userDTO, token);		
		}
		throw new EntityAlreadyExists(String.format("Name '%s' unavailable.", user.getName()));
	}
	
	@Transactional
	public UserDTO updatePartial(Map<String, Object> fields, Long id) {
		User userDestiny = find(id);
		
		ObjectMapper objMapper = new ObjectMapper();
		objMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
		User userFields = objMapper.convertValue(fields, User.class);
		
		fields.forEach((propertyName, propertyValue) -> {
			field = ReflectionUtils.findField(User.class, propertyName);
			field.setAccessible(true);
			
			Object newValue = ReflectionUtils.getField(field, userFields);	
			ReflectionUtils.setField(field, userDestiny, newValue);
		});	
		return UserDTO.fromEntity(repository.save(userDestiny));
	}
	
	@Transactional
	public UserDTO update(User userAtt, Long id) {
		User currentUser = find(id);
		User find = repository.findByName(userAtt.getName());
		
		if(find != null && find.getId() != id) {
			throw new EntityAlreadyExists(String.format("name '%s' unavailable", userAtt.getName()));
		}
		BeanUtils.copyProperties(userAtt, currentUser, "id");
		return UserDTO.fromEntity(repository.saveAndFlush(currentUser));
	}

	@Transactional
	public void delete(Long id) {
		System.out.println("OPA");
		try {
			find(id);
			repository.deleteById(id);		
		}catch(DataIntegrityViolationException e) {
			throw new EntityInUse(String.format("User with id %d cannot be deleted as it is in use.", id));
		}
	}
}
