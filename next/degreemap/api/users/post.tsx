import {ACCESS_TOKEN} from "@/context/AuthContext";

export const createAccount = async (email: string, password: string) => {
    const response = await fetch('http://localhost:8080/api/auth/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: new URLSearchParams({
            email, password
        })
      });

    const data = await response.json();

    if(response.ok){
        localStorage.setItem(ACCESS_TOKEN, data.accessToken);
        return data.accesstoken;
    } else {
        throw new Error(data.message || 'Failed to login');
    }
}