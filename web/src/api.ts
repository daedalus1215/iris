import axios from 'axios'

const api = axios.create({
  baseURL: 'http://192.168.1.193:3000', // Adjust to your NestJS backend URL
})

export default api
