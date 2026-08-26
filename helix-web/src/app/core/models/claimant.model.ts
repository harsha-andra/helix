export interface Claimant {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  city: string;
  state: string;
}

export interface Adjuster {
  id: string;
  name: string;
  email: string;
  activeClaims: number;
}
