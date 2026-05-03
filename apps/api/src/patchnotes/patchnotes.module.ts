import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { PatchnotesController } from './patchnotes.controller';
import { PatchnotesService } from './patchnotes.service';

@Module({
  imports: [AuthModule],
  controllers: [PatchnotesController],
  providers: [PatchnotesService],
})
export class PatchnotesModule {}
