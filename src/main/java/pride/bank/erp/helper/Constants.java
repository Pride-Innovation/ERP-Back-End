/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.helper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Constants {
    public static final String DELETED_SUCCESSFULLY = "Deleted successfully";
    public static final String ENTITY_NOT_FOUND = "Entity not found";
    public static final String NAME_VALIDATOR = "Must be a minimum of 3 and maximum of 20 letters";
    public static final String NULL_FIELD_VALIDATOR = "Field cannot be null";
    public static final String BLANK_FIELD_VALIDATOR = "Field cannot be blank";
    public static final String EMAIL_VALIDATOR = "Email address must be valid";
    public static final String PHONE_VALIDATOR = "Invalid Ugandan phone number";
    public static final String PHONE_REGEX = "^\\+256\\d{9}";
    public static final String PASSWORD_REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=*!])(?=\\S+$).{8,}$";
    public static final String PASSWORD_VALIDATOR = "Password should contain, Lower case letter, uppercase letter, number, a symbol like #, @, ? and its length must be at least 8";
    public static final String GENERAL_ROUTE = "/api/v1";
    public static final String READ_USER = "READ_USER";
    public static final String CREATE_USER = "CREATE_USER";
    public static final String DELETE_USER = "DELETE_USER";
    public static final String UPDATE_USER = "UPDATE_USER";
    public static final String READ_ROLE = "READ_ROLE";
    public static final String CREATE_ROLE = "CREATE_ROLE";
    public static final String DELETE_ROLE = "DELETE_ROLE";
    public static final String UPDATE_ROLE = "UPDATE_ROLE";
    public static final String READ_PERMISSION = "READ_PERMISSION";
    public static final String CREATE_PERMISSION = "CREATE_PERMISSION";
    public static final String DELETE_PERMISSION = "DELETE_PERMISSION";
    public static final String UPDATE_PERMISSION = "UPDATE_PERMISSION";
    public static final String USER_ROUTE = GENERAL_ROUTE + "/users/**";
    public static final String REGISTER_USER_ROUTE = GENERAL_ROUTE + "/auth/register/**";
    public static final String ROLE_ROUTE = GENERAL_ROUTE + "/roles/**";
    public static final String PERMISSION_ROUTE = GENERAL_ROUTE + "/permissions/**";
    public static final String AUTH_ROUTE = GENERAL_ROUTE + "/auth/login";
    public static final String LOGOUT = GENERAL_ROUTE + "/auth/logout";
    public static final String REFRESH_TOKEN_ROUTE = GENERAL_ROUTE + "/auth/refresh-token";
    public static final String REQUEST_PASSWORD_RESET = GENERAL_ROUTE + "/request-password-reset";
    public static final String RESET_PASSWORD = GENERAL_ROUTE + "/reset-password";
    public static final String VERIFY_EMAIL = GENERAL_ROUTE + "/auth/verify-email/**";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String NUMBERS = "0123456789";
    public static final String SPECIAL_CHARACTERS = "@#*!";
    public static final int PASSWORD_LENGTH = 10;
    public static final Integer DEFAULT_PAGE_SIZE = 10;
    public static final String DEFAULT_SORT_FIELD = "id";
    public static final Integer DEFAULT_PAGE_NUMBER = 0;
    public static final String ASCENDING_SORT_DIRECTION = "ASC";
    public static final String DESCENDING_SORT_DIRECTION = "DESC";
    public static final String AUDIT_ROUTE = GENERAL_ROUTE + "/audit/**";
    public static final String REQUEST_ROUTE = GENERAL_ROUTE + "/requests/**";
    public static final String STATUS_ROUTE = GENERAL_ROUTE + "/statuses/**";
    public static final String READ_REQUEST = "READ_REQUEST";
    public static final String CREATE_REQUEST = "CREATE_REQUEST";
    public static final String DELETE_REQUEST = "DELETE_REQUEST";
    public static final String UPDATE_REQUEST = "UPDATE_REQUEST";
    public static final String CREATE_STATUS = "CREATE_STATUS";
    public static final String DELETE_STATUS = "DELETE_STATUS";
    public static final String UPDATE_STATUS = "UPDATE_STATUS";

}
