import { Global, Module } from '@nestjs/common';
import { LoginAnomalyService } from './login-anomaly.service';

@Global()
@Module({
  providers: [LoginAnomalyService],
  exports: [LoginAnomalyService],
})
export class SecurityModule {}
