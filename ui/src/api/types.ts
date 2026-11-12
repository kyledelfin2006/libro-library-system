import type { components } from './schema';

type Schemas = components['schemas'];
type RequiredFields<T, K extends keyof T> = T & Required<Pick<T, K>>;

export type UserRole = NonNullable<Schemas['UserResponseDTO']['userRole']>;
export type UserCourse = NonNullable<Schemas['UserCreateRequestDTO']['userCourse']>;
export type UserITMajor = NonNullable<Schemas['UserCreateRequestDTO']['major']>;

// Springdoc leaves some response properties optional even though the API always supplies them.
export type TokenResponse = RequiredFields<Schemas['TokenResponseDTO'],
  'accessToken' | 'tokenType' | 'expiresIn' | 'mustChangePassword'>;
export type UserResponse = RequiredFields<Schemas['UserResponseDTO'],
  'universityId' | 'firstName' | 'lastName' | 'email' | 'userRole' | 'mustChangePassword'>;
export type SignupRequest = Omit<Schemas['UserCreateRequestDTO'], 'userRole' | 'userCourse'> & {
  userCourse: UserCourse;
};
export type LoginRequest = Schemas['LoginRequestDTO'];
export type PasswordChangeRequest = Schemas['PasswordChangeRequestDTO'];
export type BookResponse = RequiredFields<Schemas['BookResponseDTO'], 'id' | 'title' | 'author' | 'genre'>;
export type LoanResponse = RequiredFields<Schemas['LoanResponseDTO'],
  'loanId' | 'bookId' | 'bookTitle' | 'borrowedAt' | 'dueAt' | 'status' | 'overdue'>;
export type ErrorBody = Schemas['ErrorResponse'];

/** Spring Data page as the API serializes it, with the fields the UI reads made required. */
export interface Page<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
